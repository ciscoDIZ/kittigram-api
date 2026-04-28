package es.kitti.organization.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import es.kitti.organization.dto.OrganizationPublicMinimalResponse;
import es.kitti.organization.repository.OrganizationRepository;
import es.kitti.organization.security.InternalOnly;
import io.quarkus.hibernate.reactive.panache.common.WithSession;

import java.util.List;

@Path("/organizations/internal")
@Produces(MediaType.APPLICATION_JSON)
@InternalOnly
public class OrganizationInternalResource {

    @Inject
    OrganizationRepository repository;

    @GET
    @Path("/by-region/{region}")
    @WithSession
    public Uni<List<OrganizationPublicMinimalResponse>> findByRegion(@PathParam("region") String region) {
        return repository.findActiveByRegion(region)
                .onItem().transform(list -> list.stream()
                        .map(o -> new OrganizationPublicMinimalResponse(
                                o.id, o.name, o.city, o.region, o.phone, o.email))
                        .toList());
    }
}
