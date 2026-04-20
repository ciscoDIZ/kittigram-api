package es.kitti.gateway.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import es.kitti.gateway.proxy.ProxyService;
import es.kitti.gateway.ratelimit.RateLimitedProxy;

@Path("/api")
public class GatewayResource {

    @Inject
    ProxyService proxyService;

    @Inject
    RateLimitedProxy rateLimitedProxy;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @POST
    @Path("/auth/login")
    @PermitAll
    public Uni<Response> login(@Context HttpHeaders headers,
                               @Context RoutingContext rc,
                               byte[] body) {
        String key = extractEmail(body);
        if (key == null) key = clientIp(headers, rc);
        return rateLimitedProxy.proxyLogin(key, body,
                headers.getHeaderString("Authorization"),
                headers.getHeaderString("Content-Type"));
    }

    @POST
    @Path("/auth/refresh")
    @PermitAll
    public Uni<Response> refresh(@Context HttpHeaders headers,
                                 @Context RoutingContext rc,
                                 byte[] body) {
        return rateLimitedProxy.proxyRefresh(clientIp(headers, rc), body,
                headers.getHeaderString("Authorization"),
                headers.getHeaderString("Content-Type"));
    }

    @POST
    @Path("/auth/logout")
    @PermitAll
    public Uni<Response> logout(@Context HttpHeaders headers, byte[] body) {
        return proxyService.proxy("POST", "/api/auth/logout", body,
                headers.getHeaderString("Authorization"),
                headers.getHeaderString("Content-Type"));
    }

    @POST
    @Path("/storage/upload")
    public Uni<Response> storageUpload(@Context HttpHeaders headers,
                                       @Context RoutingContext rc,
                                       byte[] body) {
        return rateLimitedProxy.proxyStorageUpload(clientIp(headers, rc), body,
                headers.getHeaderString("Authorization"),
                headers.getHeaderString("Content-Type"));
    }

    @GET
    @Path("/{path: .+}")
    public Uni<Response> get(@PathParam("path") String path,
                             @Context HttpHeaders headers) {
        return proxyService.proxy("GET", "/api/" + path, null,
                headers.getHeaderString("Authorization"), null);
    }

    @POST
    @Path("/{path: .+}")
    public Uni<Response> post(@PathParam("path") String path,
                              @Context HttpHeaders headers,
                              byte[] body) {
        return proxyService.proxy("POST", "/api/" + path, body,
                headers.getHeaderString("Authorization"),
                headers.getHeaderString("Content-Type"));
    }

    @PUT
    @Path("/{path: .+}")
    public Uni<Response> put(@PathParam("path") String path,
                             @Context HttpHeaders headers,
                             byte[] body) {
        return proxyService.proxy("PUT", "/api/" + path, body,
                headers.getHeaderString("Authorization"),
                headers.getHeaderString("Content-Type"));
    }

    @PATCH
    @Path("/{path: .+}")
    public Uni<Response> patch(@PathParam("path") String path,
                               @Context HttpHeaders headers,
                               byte[] body) {
        return proxyService.proxy("PATCH", "/api/" + path, body,
                headers.getHeaderString("Authorization"),
                headers.getHeaderString("Content-Type"));
    }

    @DELETE
    @Path("/{path: .+}")
    public Uni<Response> delete(@PathParam("path") String path,
                                @Context HttpHeaders headers) {
        return proxyService.proxy("DELETE", "/api/" + path, null,
                headers.getHeaderString("Authorization"), null);
    }

    private static String extractEmail(byte[] body) {
        try {
            JsonNode node = MAPPER.readTree(body);
            String email = node.path("email").asText(null);
            return (email != null && !email.isBlank()) ? email : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String clientIp(HttpHeaders headers, RoutingContext rc) {
        String forwarded = headers.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        if (rc != null && rc.request().remoteAddress() != null) {
            return rc.request().remoteAddress().host();
        }
        return "unknown";
    }
}