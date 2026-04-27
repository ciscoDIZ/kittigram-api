package es.kitti.gateway.resource;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/openapi")
@Tag(name = "OpenAPI", description = "OpenAPI specifications of each microservice")
@ApplicationScoped
@IfBuildProfile("dev")
public class OpenApiProxyResource {

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

    @GET
    @Path("/users")
    public Uni<Response> usersSpec() {
        return proxyOpenApiSpec(userServiceUrl);
    }

    @GET
    @Path("/auth")
    public Uni<Response> authSpec() {
        return proxyOpenApiSpec(authServiceUrl);
    }

    @GET
    @Path("/cats")
    public Uni<Response> catsSpec() {
        return proxyOpenApiSpec(catServiceUrl);
    }

    @GET
    @Path("/adoptions")
    public Uni<Response> adoptionsSpec() {
        return proxyOpenApiSpec(adoptionServiceUrl);
    }

    @GET
    @Path("/organizations")
    public Uni<Response> organizationsSpec() {
        return proxyOpenApiSpec(organizationServiceUrl);
    }

    @GET
    @Path("/storage")
    public Uni<Response> storageSpec() {
        return proxyOpenApiSpec(storageServiceUrl);
    }

    private Uni<Response> proxyOpenApiSpec(String serviceUrl) {
        String fullUrl = serviceUrl + "/q/openapi";
        Log.infof("Proxying OpenAPI spec from %s", fullUrl);
        return webClient.getAbs(fullUrl)
                .send()
                .onItem().transform(r -> {
                    Response.ResponseBuilder rb = Response.status(r.statusCode());
                    if (r.body() != null && r.body().length() > 0) {
                        rb.entity(r.body().getBytes())
                          .header("Content-Type", r.getHeader("Content-Type"));
                    }
                    return rb.build();
                })
                .onFailure().recoverWithItem(Response.status(Response.Status.SERVICE_UNAVAILABLE).build());
    }
}
