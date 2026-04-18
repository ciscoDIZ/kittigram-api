package org.ciscoadiz.cat.resource;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.ciscoadiz.cat.dto.CatCreateRequest;
import org.ciscoadiz.cat.dto.CatResponse;
import org.ciscoadiz.cat.dto.CatSummaryResponse;
import org.ciscoadiz.cat.dto.CatUpdateRequest;
import org.ciscoadiz.cat.service.CatService;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/cats")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class CatResource {

    @Inject
    CatService catService;

    @GET
    @PermitAll
    public Multi<CatSummaryResponse> search(
            @QueryParam("city") String city,
            @QueryParam("name") String name) {
        return catService.search(city, name);
    }

    @GET
    @Path("/{id}")
    @PermitAll
    public Uni<Response> findById(@PathParam("id") Long id) {
        return catService.findById(id)
                .onItem().transform(cat -> Response.ok(cat).build());
    }

    @POST
    public Uni<Response> createCat(@Valid CatCreateRequest request) {
        return catService.createCat(request)
                .onItem().transform(cat -> Response.status(Response.Status.CREATED)
                        .entity(cat).build());
    }

    @PUT
    @Path("/{id}")
    public Uni<Response> updateCat(
            @PathParam("id") Long id,
            @QueryParam("organizationId") Long organizationId,
            @Valid CatUpdateRequest request) {
        return catService.updateCat(id, request, organizationId)
                .onItem().transform(cat -> Response.ok(cat).build());
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> deleteCat(
            @PathParam("id") Long id,
            @QueryParam("organizationId") Long organizationId) {
        return catService.deleteCat(id, organizationId)
                .onItem().transform(v -> Response.noContent().build());
    }

    @POST
    @Path("/{id}/images")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<Response> uploadImage(
            @PathParam("id") Long id,
            @QueryParam("organizationId") Long organizationId,
            @RestForm("file") FileUpload file) {
        return catService.uploadImage(id, file, organizationId)
                .onItem().transform(cat -> Response.ok(cat).build());
    }

    @DELETE
    @Path("/{catId}/images/{imageId}")
    public Uni<Response> deleteImage(
            @PathParam("catId") Long catId,
            @PathParam("imageId") Long imageId,
            @QueryParam("organizationId") Long organizationId) {
        return catService.deleteImage(catId, imageId, organizationId)
                .onItem().transform(v -> Response.noContent().build());
    }
}