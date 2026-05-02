package es.kitti.adoption.resource;

import es.kitti.adoption.intake.repository.IntakeRequestRepository;
import es.kitti.adoption.repository.AdoptionFormRepository;
import es.kitti.adoption.repository.AdoptionRequestFormRepository;
import es.kitti.adoption.repository.AdoptionRequestRepository;
import es.kitti.adoption.security.IdNumberEncryptionService;
import es.kitti.adoption.security.InternalOnly;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/adoptions/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalOnly
public class AdoptionInternalResource {

    @Inject
    AdoptionRequestRepository adoptionRequestRepository;

    @Inject
    AdoptionFormRepository adoptionFormRepository;

    @Inject
    AdoptionRequestFormRepository adoptionRequestFormRepository;

    @Inject
    IntakeRequestRepository intakeRequestRepository;

    @Inject
    IdNumberEncryptionService encryptionService;

    @GET
    @Path("/cats/{catId}/active")
    public Uni<Boolean> hasActiveRequestsForCat(@PathParam("catId") Long catId) {
        return adoptionRequestRepository.existsActiveByCatId(catId);
    }

    @DELETE
    @Path("/users/{userId}")
    public Uni<Response> anonymizeUser(@PathParam("userId") Long userId) {
        String encryptedPlaceholder = encryptionService.encrypt("SUPRIMIDO");
        return Panache.withTransaction(() ->
                adoptionRequestRepository.findByAdopterId(userId)
                        .onItem().transformToUni(requests -> {
                            List<Long> requestIds = requests.stream().map(r -> r.id).toList();
                            return adoptionRequestRepository.anonymizeAdopter(userId)
                                    .chain(() -> adoptionFormRepository.anonymizeForRequestIds(requestIds, encryptedPlaceholder))
                                    .chain(() -> adoptionRequestFormRepository.clearAllergiesForRequestIds(requestIds))
                                    .chain(() -> intakeRequestRepository.anonymizeByUserId(userId));
                        })
        )
        .onItem().transform(v -> Response.noContent().build());
    }
}