package es.kitti.schedule.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "adoption-service")
@Path("/adoptions/internal/retention")
public interface AdoptionInternalClient {

    @POST
    @Path("/run")
    Uni<Response> triggerRetention(@HeaderParam("X-Internal-Token") String token);
}
