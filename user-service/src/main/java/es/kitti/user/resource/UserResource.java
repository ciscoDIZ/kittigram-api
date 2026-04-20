package es.kitti.user.resource;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import es.kitti.user.dto.ActivationRequest;
import es.kitti.user.dto.UserCreateRequest;
import es.kitti.user.dto.UserResponse;
import es.kitti.user.dto.UserUpdateRequest;
import es.kitti.user.service.UserService;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    @Inject
    UserService userService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("{email}")
    public Uni<Response> findByEmail(@PathParam("email") String email) {
        return userService.findByEmail(email)
                .onItem().transform(Response::ok)
                .onItem().transform(Response.ResponseBuilder::build);
    }

    @GET
    @Path("/active")
    public Multi<UserResponse> findAllActiveUsers() {
        return userService.findAllActiveUsers();
    }

    @POST
    @PermitAll
    public Uni<Response> createUser(@Valid UserCreateRequest request, @Context UriInfo uriInfo) {
        return userService.createUser(request).onItem().transform(user -> {
           var location = uriInfo.getAbsolutePathBuilder().path(user.email()).build();
           return Response.created(location).entity(user).build();
        });
    }

    @PUT
    @Path("/{email}")
    public Uni<Response> updateUser(@PathParam("email") String email,
                                    UserUpdateRequest request) {
        requireSelf(email);
        return userService.updateUser(email, request)
                .onItem().transform(user -> Response.ok(user).build());
    }

    @PUT
    @Path("/{email}/deactivate")
    public Uni<Response> deactivateUser(@PathParam("email") String email) {
        requireSelf(email);
        return userService.deactivateUser(email)
                .onItem().transform(user -> Response.ok(user).build());
    }

    @PUT
    @Path("/{email}/activate")
    public Uni<Response> activateUser(@PathParam("email") String email) {
        requireSelf(email);
        return userService.activateUser(email)
                .onItem().transform(user -> Response.ok(user).build());
    }

    @POST
    @Path("/activate")
    @PermitAll
    public Uni<Response> activate(@Valid ActivationRequest request) {
        return userService.activateByToken(request.token())
                .onItem().transform(user -> Response.ok(user).build());
    }

    private void requireSelf(String email) {
        String tokenEmail = jwt.getClaim("email");
        if (!email.equals(tokenEmail)) {
            throw new ForbiddenException("Access denied");
        }
    }
}