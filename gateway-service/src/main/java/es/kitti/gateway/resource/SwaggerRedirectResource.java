package es.kitti.gateway.resource;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@Path("/doc")
@ApplicationScoped
@PermitAll
@IfBuildProfile("dev")
public class SwaggerRedirectResource {

    @GET
    public Response redirect() {
        return Response.status(Response.Status.FOUND)
                .location(URI.create("/swagger-ui"))
                .build();
    }
}
