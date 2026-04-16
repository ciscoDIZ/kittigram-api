package org.ciscoadiz.adoption.resource;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.ciscoadiz.adoption.dto.*;
import org.ciscoadiz.adoption.service.AdoptionService;
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
    public Uni<Response> createAdoptionRequest(AdoptionRequestCreateRequest request) {
        Long adopterId = Long.parseLong(jwt.getSubject());
        String adopterEmail = jwt.getClaim("email");
        return adoptionService.createAdoptionRequest(request, adopterId, adopterEmail)
                .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build());
    }

    @GET
    @Path("/{id}")
    public Uni<AdoptionRequestResponse> findById(@PathParam("id") Long id) {
        return adoptionService.findById(id);
    }

    @GET
    @Path("/my")
    public Uni<List<AdoptionRequestResponse>> findMyAdoptions() {
        Long adopterId = Long.parseLong(jwt.getSubject());
        return adoptionService.findByAdopterId(adopterId);
    }

    @GET
    @Path("/organization")
    public Uni<List<AdoptionRequestResponse>> findByOrganization() {
        Long organizationId = Long.parseLong(jwt.getSubject());
        return adoptionService.findByOrganizationId(organizationId);
    }

    @PATCH
    @Path("/{id}/status")
    public Uni<AdoptionRequestResponse> updateStatus(
            @PathParam("id") Long id,
            AdoptionStatusUpdateRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return adoptionService.updateStatus(id, request, userId);
    }

    @POST
    @Path("/{id}/form")
    public Uni<Response> submitRequestForm(
            @PathParam("id") Long id,
            AdoptionRequestFormCreateRequest request) {
        Long adopterId = Long.parseLong(jwt.getSubject());
        return adoptionService.submitRequestForm(id, request, adopterId)
                .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build());
    }

    @POST
    @Path("/{id}/interview")
    public Uni<Response> scheduleInterview(
            @PathParam("id") Long id,
            InterviewCreateRequest request) {
        Long organizationId = Long.parseLong(jwt.getSubject());
        return adoptionService.scheduleInterview(id, request, organizationId)
                .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build());
    }

    @POST
    @Path("/{id}/adoption-form")
    public Uni<Response> submitAdoptionForm(
            @PathParam("id") Long id,
            AdoptionFormCreateRequest request) {
        Long adopterId = Long.parseLong(jwt.getSubject());
        return adoptionService.submitAdoptionForm(id, request, adopterId)
                .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build());
    }
}