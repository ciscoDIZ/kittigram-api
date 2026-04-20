package es.kitti.adoption.resource;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import es.kitti.adoption.dto.*;
import es.kitti.adoption.service.AdoptionService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/adoptions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class AdoptionResource {

    @Inject
    AdoptionService adoptionService;

    @Inject
    JsonWebToken jwt;

    @POST
    @RolesAllowed("User")
    public Uni<Response> createAdoptionRequest(@Valid AdoptionRequestCreateRequest request) {
        Long adopterId = Long.parseLong(jwt.getSubject());
        String adopterEmail = jwt.getClaim("email");
        return adoptionService.createAdoptionRequest(request, adopterId, adopterEmail)
                .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build());
    }

    @GET
    @Path("/{id}")
    public Uni<AdoptionRequestResponse> findById(@PathParam("id") Long id) {
        Long callerId = Long.parseLong(jwt.getSubject());
        return adoptionService.findById(id, callerId);
    }

    @GET
    @Path("/my")
    @RolesAllowed("User")
    public Uni<List<AdoptionRequestResponse>> findMyAdoptions() {
        Long adopterId = Long.parseLong(jwt.getSubject());
        return adoptionService.findByAdopterId(adopterId);
    }

    @GET
    @Path("/organization")
    @RolesAllowed("Organization")
    public Uni<List<AdoptionRequestResponse>> findByOrganization() {
        Long organizationId = Long.parseLong(jwt.getSubject());
        return adoptionService.findByOrganizationId(organizationId);
    }

    @PATCH
    @Path("/{id}/status")
    @RolesAllowed("Organization")
    public Uni<AdoptionRequestResponse> updateStatus(
            @PathParam("id") Long id,
            @Valid AdoptionStatusUpdateRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return adoptionService.updateStatus(id, request, userId);
    }

    @POST
    @Path("/{id}/form")
    @RolesAllowed("User")
    public Uni<Response> submitRequestForm(
            @PathParam("id") Long id,
            @Valid AdoptionRequestFormCreateRequest request) {
        Long adopterId = Long.parseLong(jwt.getSubject());
        return adoptionService.submitRequestForm(id, request, adopterId)
                .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build());
    }

    @POST
    @Path("/{id}/interview")
    @RolesAllowed("Organization")
    public Uni<Response> scheduleInterview(
            @PathParam("id") Long id,
            @Valid InterviewCreateRequest request) {
        Long organizationId = Long.parseLong(jwt.getSubject());
        return adoptionService.scheduleInterview(id, request, organizationId)
                .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build());
    }

    @POST
    @Path("/{id}/adoption-form")
    @RolesAllowed("User")
    public Uni<Response> submitAdoptionForm(
            @PathParam("id") Long id,
            @Valid AdoptionFormCreateRequest request) {
        Long adopterId = Long.parseLong(jwt.getSubject());
        return adoptionService.submitAdoptionForm(id, request, adopterId)
                .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build());
    }
}