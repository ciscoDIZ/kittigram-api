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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Users", description = "User account management")
public class UserResource {

    @Inject
    UserService userService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("{email}")
    @Operation(summary = "Get user by email", description = "Retrieve user details. Only the user themselves can access their data.")
    @SecurityRequirement(name = "BearerAuth")
    @APIResponse(responseCode = "200", description = "User found", content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden - user is trying to access another user's data")
    @APIResponse(responseCode = "404", description = "User not found")
    public Uni<Response> findByEmail(
            @Parameter(description = "User email") @PathParam("email") String email) {
        requireSelf(email);
        return userService.findByEmail(email)
                .onItem().transform(Response::ok)
                .onItem().transform(Response.ResponseBuilder::build);
    }

    @GET
    @Path("/active")
    @Operation(summary = "List active users", description = "Retrieve all users with Active status")
    @SecurityRequirement(name = "BearerAuth")
    @APIResponse(responseCode = "200", description = "List of active users", content = @Content(schema = @Schema(implementation = UserResponse[].class)))
    public Multi<UserResponse> findAllActiveUsers() {
        return userService.findAllActiveUsers();
    }

    @POST
    @PermitAll
    @Operation(summary = "Register new user", description = "Create a new user account. The user starts with Pending status and must activate via email token.")
    @APIResponse(responseCode = "201", description = "User created successfully", content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request (email format, password too short, etc.)")
    @APIResponse(responseCode = "409", description = "Email already registered")
    public Uni<Response> createUser(
            @Valid UserCreateRequest request,
            @Context UriInfo uriInfo) {
        return userService.createUser(request).onItem().transform(user -> {
           var location = uriInfo.getAbsolutePathBuilder().path(user.email()).build();
           return Response.created(location).entity(user).build();
        });
    }

    @PUT
    @Path("/{email}")
    @Operation(summary = "Update user profile", description = "Update user's name, surname, or birthdate. Only the user themselves can update their data.")
    @SecurityRequirement(name = "BearerAuth")
    @APIResponse(responseCode = "200", description = "User updated", content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden")
    public Uni<Response> updateUser(
            @Parameter(description = "User email") @PathParam("email") String email,
            UserUpdateRequest request) {
        requireSelf(email);
        return userService.updateUser(email, request)
                .onItem().transform(user -> Response.ok(user).build());
    }

    @PUT
    @Path("/{email}/deactivate")
    @Operation(summary = "Deactivate user account", description = "Set user status to Inactive (soft delete)")
    @SecurityRequirement(name = "BearerAuth")
    @APIResponse(responseCode = "200", description = "User deactivated", content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden")
    public Uni<Response> deactivateUser(
            @Parameter(description = "User email") @PathParam("email") String email) {
        requireSelf(email);
        return userService.deactivateUser(email)
                .onItem().transform(user -> Response.ok(user).build());
    }

    @PUT
    @Path("/{email}/activate")
    @Operation(summary = "Force-activate user account", description = "Set user status to Active directly (admin/self)")
    @SecurityRequirement(name = "BearerAuth")
    @APIResponse(responseCode = "200", description = "User activated", content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden")
    public Uni<Response> activateUser(
            @Parameter(description = "User email") @PathParam("email") String email) {
        requireSelf(email);
        return userService.activateUser(email)
                .onItem().transform(user -> Response.ok(user).build());
    }

    @POST
    @Path("/activate")
    @PermitAll
    @Operation(summary = "Activate user account by token", description = "Complete account registration using the activation token sent via email")
    @APIResponse(responseCode = "200", description = "User activated", content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid or expired activation token")
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