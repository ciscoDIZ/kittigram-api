package es.kitti.user.resource;

import es.kitti.user.service.ErasureService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Users", description = "User account management")
public class UserErasureResource {

    @Inject
    ErasureService erasureService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/me/erasure-request")
    @Operation(
            summary = "Request account erasure (Right to be Forgotten)",
            description = "Art. 17 RGPD. Deactivates the account immediately and schedules irreversible " +
                    "anonymisation in 30 days. Returns 409 if a legal hold is active."
    )
    @SecurityRequirement(name = "BearerAuth")
    @APIResponse(responseCode = "202", description = "Erasure request accepted")
    @APIResponse(responseCode = "409", description = "Legal hold active — erasure blocked")
    public Uni<Response> requestErasure(@Context HttpHeaders headers) {
        Long userId = Long.parseLong(jwt.getSubject());
        String ip = extractIp(headers);
        return erasureService.requestErasure(userId, ip)
                .onItem().transform(v -> Response.accepted().build());
    }

    @PUT
    @Path("/{userId}/legal-hold")
    @RolesAllowed("Admin")
    @Operation(
            summary = "Set or clear legal hold (Art. 17.3.e RGPD)",
            description = "Admin only. Pass holdUntil in ISO-8601 to activate hold, null to clear it. " +
                    "While active, any erasure request returns 409."
    )
    @SecurityRequirement(name = "BearerAuth")
    @APIResponse(responseCode = "204", description = "Legal hold updated")
    public Uni<Response> setLegalHold(
            @PathParam("userId") Long userId,
            @QueryParam("holdUntil") String holdUntilIso) {
        LocalDateTime holdUntil = holdUntilIso != null ? LocalDateTime.parse(holdUntilIso) : null;
        return erasureService.setLegalHold(userId, holdUntil)
                .onItem().transform(v -> Response.noContent().build());
    }

    private String extractIp(HttpHeaders headers) {
        String forwarded = headers.getHeaderString("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : null;
    }
}