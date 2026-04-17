package org.ciscoadiz.gateway.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.ciscoadiz.gateway.proxy.ProxyService;

@Path("/api")
public class GatewayResource {

    @Inject
    ProxyService proxyService;

    @GET
    @Path("/{path: .+}")
    public Uni<Response> get(@PathParam("path") String path,
                             @Context HttpHeaders headers) {
        return proxyService.proxy(
                "GET",
                "/api/" + path,
                null,
                headers.getHeaderString("Authorization"),
                null
        );
    }

    @POST
    @Path("/{path: .+}")
    public Uni<Response> post(@PathParam("path") String path,
                              @Context HttpHeaders headers,
                              byte[] body) {
        return proxyService.proxy(
                "POST",
                "/api/" + path,
                body,
                headers.getHeaderString("Authorization"),
                headers.getHeaderString("Content-Type")
        );
    }

    @PUT
    @Path("/{path: .+}")
    public Uni<Response> put(@PathParam("path") String path,
                             @Context HttpHeaders headers,
                             byte[] body) {
        return proxyService.proxy(
                "PUT",
                "/api/" + path,
                body,
                headers.getHeaderString("Authorization"),
                headers.getHeaderString("Content-Type")
        );
    }

    @PATCH
    @Path("/{path: .+}")
    public Uni<Response> patch(@PathParam("path") String path,
                               @Context HttpHeaders headers,
                               byte[] body) {
        return proxyService.proxy(
                "PATCH",
                "/api/" + path,
                body,
                headers.getHeaderString("Authorization"),
                headers.getHeaderString("Content-Type")
        );
    }

    @DELETE
    @Path("/{path: .+}")
    public Uni<Response> delete(@PathParam("path") String path,
                                @Context HttpHeaders headers) {
        return proxyService.proxy(
                "DELETE",
                "/api/" + path,
                null,
                headers.getHeaderString("Authorization"),
                null
        );
    }
}