package es.kitti.gateway.proxy;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ProxyService {

    @Inject
    WebClient webClient;

    @ConfigProperty(name = "quarkus.rest-client.user-service.url")
    String userServiceUrl;

    @ConfigProperty(name = "quarkus.rest-client.auth-service.url")
    String authServiceUrl;

    @ConfigProperty(name = "quarkus.rest-client.cat-service.url")
    String catServiceUrl;

    @ConfigProperty(name = "quarkus.rest-client.storage-service.url")
    String storageServiceUrl;

    @ConfigProperty(name = "quarkus.rest-client.adoption-service.url")
    String adoptionServiceUrl;

    @ConfigProperty(name = "quarkus.rest-client.organization-service.url")
    String organizationServiceUrl;

    public Uni<Response> proxy(String method, String path,
                               byte[] body, String authHeader,
                               String contentType) {
        String targetUrl = resolveTarget(path);
        if (targetUrl == null) {
            return Uni.createFrom().item(
                    Response.status(Response.Status.NOT_FOUND).build()
            );
        }

        String targetPath = path.replaceFirst("^/api", "");
        Log.infof("Proxying %s %s → %s%s", method, path, targetUrl, targetPath);
        var request = webClient.requestAbs(
                io.vertx.core.http.HttpMethod.valueOf(method),
                targetUrl + targetPath
        );

        if (authHeader != null) {
            request = request.putHeader("Authorization", authHeader);
        }
        if (contentType != null) {
            request = request.putHeader("Content-Type", contentType);
        }

        Uni<io.vertx.mutiny.ext.web.client.HttpResponse<Buffer>> response;
        if (body != null && body.length > 0) {
            response = request.sendBuffer(Buffer.buffer(body));
        } else {
            response = request.send();
        }

        return response.onItem().transform(r -> {
            Response.ResponseBuilder rb = Response.status(r.statusCode());
            if (r.body() != null && r.body().length() > 0) {
                rb.entity(r.body().getBytes())
                  .header("Content-Type", r.getHeader("Content-Type"));
            }
            return rb.build();
        });
    }

    private String resolveTarget(String path) {
        if (path.startsWith("/api/auth")) return authServiceUrl;
        if (path.startsWith("/api/users")) return userServiceUrl;
        if (path.startsWith("/api/cats")) return catServiceUrl;
        if (path.startsWith("/api/storage")) return storageServiceUrl;
        if (path.startsWith("/api/adoptions")) return adoptionServiceUrl;
        if (path.startsWith("/api/organizations")) return organizationServiceUrl;
        return null;
    }
}