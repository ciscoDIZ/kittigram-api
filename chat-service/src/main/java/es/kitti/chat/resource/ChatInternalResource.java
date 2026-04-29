package es.kitti.chat.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import es.kitti.chat.dto.CreateConversationRequest;
import es.kitti.chat.security.InternalOnly;
import es.kitti.chat.service.ChatService;

@Path("/chats/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalOnly
public class ChatInternalResource {

    @Inject
    ChatService service;

    @POST
    @Path("/conversations")
    public Uni<Response> createConversation(@Valid CreateConversationRequest request) {
        return service.createConversation(request)
                .onItem().transform(c -> Response.status(Response.Status.CREATED).entity(c).build());
    }
}