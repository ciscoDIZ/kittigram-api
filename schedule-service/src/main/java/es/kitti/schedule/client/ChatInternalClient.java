package es.kitti.schedule.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "chat-service")
@Path("/chats/internal/retention")
public interface ChatInternalClient {

    @POST
    @Path("/run")
    Uni<Response> triggerRetention(@HeaderParam("X-Internal-Token") String token);
}
