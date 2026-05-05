package es.kitti.adoption.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "user-service")
@Path("/users/internal")
@Produces(MediaType.APPLICATION_JSON)
public interface UserServiceClient {

    @GET
    @Path("/{id}")
    Uni<UserSummary> findById(
            @PathParam("id") Long id,
            @HeaderParam("X-Internal-Token") String internalToken);

    record UserSummary(Long id, String email, String name) {}
}