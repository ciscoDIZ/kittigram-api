package es.kitti.schedule.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "user-service")
@Path("/users/internal/purge")
public interface UserInternalClient {

    @POST
    @Path("/erasure")
    Uni<Response> triggerErasurePurge(@HeaderParam("X-Internal-Token") String token);

    @POST
    @Path("/activations")
    Uni<Response> triggerActivationPurge(@HeaderParam("X-Internal-Token") String token);
}
