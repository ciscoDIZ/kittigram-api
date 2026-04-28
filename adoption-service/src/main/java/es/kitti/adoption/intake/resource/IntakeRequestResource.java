package es.kitti.adoption.intake.resource;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import es.kitti.adoption.intake.dto.IntakeDecisionRequest;
import es.kitti.adoption.intake.dto.IntakeRequestCreateRequest;
import es.kitti.adoption.intake.dto.IntakeRequestResponse;
import es.kitti.adoption.intake.service.IntakeRequestService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/intake-requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class IntakeRequestResource {

    @Inject
    IntakeRequestService service;

    @Inject
    JsonWebToken jwt;

    @POST
    @RolesAllowed("User")
    public Uni<Response> create(@Valid IntakeRequestCreateRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return service.create(request, userId)
                .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build());
    }

    @GET
    @Path("/mine")
    @RolesAllowed("User")
    public Uni<List<IntakeRequestResponse>> findMine() {
        Long userId = Long.parseLong(jwt.getSubject());
        return service.findMine(userId);
    }

    @GET
    @Path("/organization")
    @RolesAllowed("Organization")
    public Uni<List<IntakeRequestResponse>> findByOrganization() {
        Long callerOrgId = Long.parseLong(jwt.getSubject());
        return service.findByOrganization(callerOrgId);
    }

    @PATCH
    @Path("/{id}/approve")
    @RolesAllowed("Organization")
    public Uni<IntakeRequestResponse> approve(@PathParam("id") Long id) {
        Long callerOrgId = Long.parseLong(jwt.getSubject());
        return service.approve(id, callerOrgId);
    }

    @PATCH
    @Path("/{id}/reject")
    @RolesAllowed("Organization")
    public Uni<IntakeRequestResponse> reject(@PathParam("id") Long id,
                                             @Valid IntakeDecisionRequest decision) {
        Long callerOrgId = Long.parseLong(jwt.getSubject());
        return service.reject(id, decision, callerOrgId);
    }
}