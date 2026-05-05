package es.kitti.user.resource;

import es.kitti.user.dto.UserResponse;
import es.kitti.user.security.InternalOnly;
import es.kitti.user.service.ErasureService;
import es.kitti.user.service.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/users/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalOnly
public class UserInternalResource {

    @Inject
    ErasureService erasureService;

    @Inject
    UserService userService;

    @GET
    @Path("/{id}")
    public Uni<UserResponse> findById(@PathParam("id") Long id) {
        return userService.findById(id);
    }

    @POST
    @Path("/purge/erasure")
    public Uni<Response> triggerErasurePurge() {
        return erasureService.purgeEligibleUsers()
                .onItem().transform(v -> Response.noContent().build());
    }

    @POST
    @Path("/purge/activations")
    public Uni<Response> triggerActivationPurge() {
        return erasureService.purgeExpiredUnactivatedUsers()
                .onItem().transform(v -> Response.noContent().build());
    }
}
