package es.kitti.user.service;

import es.kitti.user.client.AdoptionInternalClient;
import es.kitti.user.client.AuthInternalClient;
import es.kitti.user.client.ChatInternalClient;
import es.kitti.user.entity.ErasureRequest;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.exception.LegalHoldException;
import es.kitti.user.exception.UserNotFoundException;
import es.kitti.user.repository.ErasureRequestRepository;
import es.kitti.user.repository.UserRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDateTime;

@ApplicationScoped
public class ErasureService {

    @Inject
    UserRepository userRepository;

    @Inject
    ErasureRequestRepository erasureRequestRepository;

    @Inject
    @RestClient
    AuthInternalClient authInternalClient;

    @Inject
    @RestClient
    AdoptionInternalClient adoptionInternalClient;

    @Inject
    @RestClient
    ChatInternalClient chatInternalClient;

    @ConfigProperty(name = "kitties.internal.secret")
    String internalSecret;

    public Uni<Void> requestErasure(Long userId, String requestIp) {
        return Panache.withTransaction(() ->
                userRepository.findById(userId)
                        .onItem().ifNull().failWith(() -> new UserNotFoundException(String.valueOf(userId)))
                        .onItem().transformToUni(user -> {
                            if (user.legalHoldUntil != null && user.legalHoldUntil.isAfter(LocalDateTime.now())) {
                                return Uni.createFrom().failure(new LegalHoldException());
                            }
                            LocalDateTime now = LocalDateTime.now();
                            user.status = UserStatus.Inactive;
                            user.deletedAt = now;
                            user.scheduledPurgeAt = now.plusDays(30);

                            ErasureRequest er = new ErasureRequest();
                            er.userId = user.id;
                            er.requestedAt = now;
                            er.requestedIp = requestIp;
                            er.scheduledPurgeAt = user.scheduledPurgeAt;

                            return userRepository.persist(user)
                                    .chain(() -> erasureRequestRepository.persist(er));
                        })
        )
        .replaceWithVoid()
        .chain(() -> authInternalClient.deleteTokensByUser(userId, internalSecret)
                .replaceWithVoid()
                .onFailure().recoverWithUni(e -> {
                    Log.warnf("Could not delete auth tokens for user %d (will retry at purge): %s", userId, e.getMessage());
                    return Uni.createFrom().voidItem();
                })
        );
    }

    @WithTransaction
    public Uni<Void> setLegalHold(Long userId, LocalDateTime holdUntil) {
        return userRepository.findById(userId)
                .onItem().ifNull().failWith(() -> new UserNotFoundException(String.valueOf(userId)))
                .onItem().transformToUni(user -> {
                    user.legalHoldUntil = holdUntil;
                    return userRepository.persist(user);
                })
                .replaceWithVoid();
    }

    @WithTransaction
    public Uni<Void> purgeExpiredUnactivatedUsers() {
        return userRepository.deleteExpiredUnactivated()
                .invoke(count -> Log.infof("Purged %d expired unactivated users", count))
                .replaceWithVoid();
    }

    @WithSession
    public Uni<Void> purgeEligibleUsers() {
        return erasureRequestRepository.findEligibleForPurge()
                .onItem().transformToUni(requests ->
                        Multi.createFrom().iterable(requests)
                                .onItem().transformToUniAndMerge(this::purgeUser)
                                .collect().asList()
                )
                .replaceWithVoid();
    }

    private Uni<Void> purgeUser(ErasureRequest er) {
        Long userId = er.userId;
        return Panache.withSession(() -> userRepository.findById(userId))
                .onItem().transformToUni(user -> {
                    if (user != null
                            && user.legalHoldUntil != null
                            && user.legalHoldUntil.isAfter(LocalDateTime.now())) {
                        return Panache.withTransaction(() -> {
                            if (!er.blockedByHold) {
                                er.blockedByHold = true;
                                return erasureRequestRepository.<ErasureRequest>persist(er).replaceWithVoid();
                            }
                            return Uni.createFrom().voidItem();
                        });
                    }
                    return executeAnonymization(userId, er);
                })
                .onFailure().invoke(e -> Log.errorf("Error processing erasure for user %d: %s", userId, e.getMessage()))
                .onFailure().recoverWithUni(e -> Uni.createFrom().voidItem());
    }

    private Uni<Void> executeAnonymization(Long userId, ErasureRequest er) {
        return authInternalClient.deleteTokensByUser(userId, internalSecret).replaceWithVoid()
                .onFailure().recoverWithUni(e -> {
                    Log.warnf("Auth token deletion failed for user %d: %s", userId, e.getMessage());
                    return Uni.createFrom().voidItem();
                })
                .chain(() -> adoptionInternalClient.anonymizeUser(userId, internalSecret).replaceWithVoid())
                .chain(() -> chatInternalClient.anonymizeUser(userId, internalSecret).replaceWithVoid())
                .chain(() -> Panache.withTransaction(() ->
                        userRepository.delete("id = ?1", userId)
                                .chain(count -> {
                                    er.purgedAt = LocalDateTime.now();
                                    return erasureRequestRepository.<ErasureRequest>persist(er).replaceWithVoid();
                                })
                ));
    }
}