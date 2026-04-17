package org.ciscoadiz.auth.resource;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.ciscoadiz.auth.dto.AuthRequest;
import org.ciscoadiz.auth.dto.LogoutRequest;
import org.ciscoadiz.auth.dto.RefreshRequest;
import org.ciscoadiz.auth.service.AuthService;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Uni<Response> login(@Valid AuthRequest request) {
        return authService.authenticate(request)
                .onItem().transform(response -> Response.ok(response).build());
    }

    @POST
    @Path("/refresh")
    public Uni<Response> refresh(@Valid RefreshRequest request) {
        Log.infof("AuthResource refresh called with: %s", request);
        return authService.refresh(request)
                .onItem().transform(response -> Response.ok(response).build());
    }

    @POST
    @Path("/logout")
    public Uni<Response> logout(@Valid LogoutRequest request) {
        return authService.logout(request.refreshToken())
                .onItem().transform(v -> Response.noContent().build());
    }
}