package es.kitti.chat.resource;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import es.kitti.chat.dto.ConversationResponse;
import es.kitti.chat.dto.MessageResponse;
import es.kitti.chat.dto.SendMessageRequest;
import es.kitti.chat.entity.SenderType;
import es.kitti.chat.service.ChatService;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/chats")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ChatResource {

    @Inject
    ChatService service;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/mine")
    @RolesAllowed("User")
    public Uni<List<ConversationResponse>> findMineAsUser() {
        Long userId = Long.parseLong(jwt.getSubject());
        return service.findMineAsUser(userId);
    }

    @GET
    @Path("/organization")
    @RolesAllowed("Organization")
    public Uni<List<ConversationResponse>> findMineAsOrganization() {
        Long orgId = Long.parseLong(jwt.getSubject());
        return service.findMineAsOrganization(orgId);
    }

    @GET
    @Path("/{id}/messages")
    public Uni<List<MessageResponse>> listMessages(@PathParam("id") Long id) {
        return service.listMessages(id, callerId(), callerType());
    }

    @POST
    @Path("/{id}/messages")
    public Uni<Response> sendMessage(@PathParam("id") Long id,
                                     @Valid SendMessageRequest request) {
        return service.sendMessage(id, request, callerId(), callerType())
                .onItem().transform(m -> Response.status(Response.Status.CREATED).entity(m).build());
    }

    private Long callerId() {
        return Long.parseLong(jwt.getSubject());
    }

    private SenderType callerType() {
        return jwt.getGroups().contains("Organization") ? SenderType.Organization : SenderType.User;
    }
}