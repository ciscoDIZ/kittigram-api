package es.kitti.schedule.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "auth-service")
@Path("/auth/internal/purge")
public interface AuthInternalClient {

    @POST
    @Path("/tokens")
    Uni<Response> triggerTokenPurge(@HeaderParam("X-Internal-Token") String token);
}