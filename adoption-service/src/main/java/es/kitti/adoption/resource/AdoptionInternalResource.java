package es.kitti.adoption.resource;

import es.kitti.adoption.repository.AdoptionRequestRepository;
import es.kitti.adoption.security.InternalOnly;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/adoptions/internal")
@Produces(MediaType.APPLICATION_JSON)
@InternalOnly
public class AdoptionInternalResource {

    @Inject
    AdoptionRequestRepository adoptionRequestRepository;

    @GET
    @Path("/cats/{catId}/active")
    public Uni<Boolean> hasActiveRequestsForCat(@PathParam("catId") Long catId) {
        return adoptionRequestRepository.existsActiveByCatId(catId);
    }
}