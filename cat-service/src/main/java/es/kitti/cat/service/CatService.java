package es.kitti.cat.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import es.kitti.cat.client.AdoptionClient;
import es.kitti.cat.dto.*;
import es.kitti.cat.entity.Cat;
import es.kitti.cat.entity.CatImage;
import es.kitti.cat.entity.CatStatus;
import es.kitti.cat.exception.CatHasActiveAdoptionsException;
import es.kitti.cat.exception.CatNotFoundException;
import es.kitti.cat.mapper.CatMapper;
import es.kitti.cat.repository.CatImageRepository;
import es.kitti.cat.repository.CatRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import es.kitti.cat.client.StorageClient;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

@ApplicationScoped
public class CatService {

    @Inject
    CatRepository catRepository;

    @Inject
    CatImageRepository catImageRepository;

    @Inject
    CatMapper catMapper;

    @RestClient
    StorageClient storageClient;

    @RestClient
    AdoptionClient adoptionClient;

    @ConfigProperty(name = "kitties.internal.secret")
    String internalSecret;

    @WithTransaction
    public Uni<CatResponse> createCat(CatCreateRequest request, Long callerId) {
        Cat cat = catMapper.toEntity(request);
        cat.organizationId = callerId;
        return catRepository.persist(cat)
                .onItem().transform(saved -> catMapper.toResponse(saved, List.of()));
    }

    @WithTransaction
    public Uni<CatResponse> updateCat(Long id, CatUpdateRequest request, Long organizationId) {
        return catRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> new CatNotFoundException(id))
                .onItem().transformToUni(cat -> {
                    requireOwner(cat, organizationId);
                    catMapper.updateEntity(cat, request);
                    return catRepository.persist(cat);
                })
                .onItem().transformToUni(cat ->
                        catImageRepository.findByCatId(cat.id)
                                .collect().asList()
                                .onItem().transform(images -> catMapper.toResponse(cat, images))
                );
    }

    @WithSession
    public Uni<CatResponse> findById(Long id) {
        return catRepository.findById(id)
                .onItem().ifNull()
                .failWith(() -> new CatNotFoundException(id))
                .onItem().transformToUni(cat -> {
                    if (cat.status == CatStatus.Deleted) {
                        return Uni.createFrom().failure(new CatNotFoundException(id));
                    }
                    return catImageRepository.findByCatId(cat.id)
                            .collect().asList()
                            .onItem().transform(images -> catMapper.toResponse(cat, images));
                });
    }

    public Multi<CatSummaryResponse> search(String city, String name) {
        Uni<List<Cat>> catsUni;
        if (city != null && name != null) {
            catsUni = catRepository.findByCityAndName(city, name);
        } else if (city != null) {
            catsUni = catRepository.findByCity(city);
        } else if (name != null) {
            catsUni = catRepository.findByName(name);
        } else {
            catsUni = catRepository.findAvailable();
        }

        return catsUni
                .onItem().transformToMulti(list -> Multi.createFrom().iterable(list))
                .onItem().transform(catMapper::toSummaryResponse);
    }

    @WithSession
    public Uni<List<CatSummaryResponse>> findMine(Long organizationId) {
        return catRepository.findByOrganizationId(organizationId)
                .onItem().transform(cats -> cats.stream()
                        .map(catMapper::toSummaryResponse)
                        .toList());
    }

    @WithTransaction
    public Uni<CatResponse> uploadImage(Long catId, FileUpload file, Long organizationId) {
        return catRepository.findById(catId)
                .onItem().ifNull()
                .failWith(() -> new CatNotFoundException(catId))
                .onItem().transformToUni(cat -> {
                    requireOwner(cat, organizationId);
                    return storageClient.upload(file)
                            .onItem().transformToUni(storage -> {
                                CatImage image = new CatImage();
                                image.catId = catId;
                                image.key = storage.key();
                                image.url = storage.url();
                                image.imageOrder = 0;

                                if (cat.profileImageUrl == null) {
                                    cat.profileImageUrl = storage.url();
                                }

                                return catRepository.persist(cat)
                                        .onItem().transformToUni(savedCat ->
                                                catImageRepository.persist(image)
                                        );
                            })
                            .onItem().transformToUni(savedImage ->
                                    catRepository.findById(catId)
                                            .onItem().transformToUni(savedCat ->
                                                    catImageRepository.findByCatId(catId)
                                                            .collect().asList()
                                                            .onItem().transform(images ->
                                                                    catMapper.toResponse(savedCat, images)
                                                            )
                                            )
                            );
                });
    }

    @WithTransaction
    public Uni<Void> deleteImage(Long catId, Long imageId, Long organizationId) {
        return catRepository.findById(catId)
                .onItem().ifNull()
                .failWith(() -> new CatNotFoundException(catId))
                .onItem().transformToUni(cat -> {
                    requireOwner(cat, organizationId);
                    return catImageRepository.findById(imageId)
                            .onItem().ifNull()
                            .failWith(() -> new CatNotFoundException(imageId))
                            .onItem().transformToUni(image ->
                                    storageClient.delete(image.key)
                                            .onItem().transformToUni(v -> catImageRepository.delete(image))
                            );
                });
    }

    public Uni<Void> deleteCat(Long id, Long organizationId) {
        // Step 1: verify existence and ownership (session, no transaction)
        return Panache.withSession(() ->
                catRepository.findById(id)
                        .onItem().ifNull().failWith(() -> new CatNotFoundException(id))
                        .onItem().invoke(cat -> requireOwner(cat, organizationId))
                        .replaceWithVoid()
        )
        // Step 2: check adoption-service (outside any DB session)
        .onItem().transformToUni(__ ->
                adoptionClient.hasActiveRequestsForCat(id, internalSecret)
        )
        // Step 3: logical delete in its own transaction
        .onItem().transformToUni(hasActive -> {
            if (hasActive) {
                return Uni.createFrom().failure(new CatHasActiveAdoptionsException(id));
            }
            return Panache.withTransaction(() ->
                    catRepository.findById(id)
                            .onItem().transformToUni(cat -> {
                                cat.status = CatStatus.Deleted;
                                return catRepository.persist(cat).replaceWithVoid();
                            })
            );
        });
    }

    private void requireOwner(Cat cat, Long organizationId) {
        if (!cat.organizationId.equals(organizationId)) {
            throw new ForbiddenException("Access denied");
        }
    }
}