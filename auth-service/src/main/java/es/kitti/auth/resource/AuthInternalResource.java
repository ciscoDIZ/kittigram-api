package es.kitti.auth.resource;

import es.kitti.auth.repository.RefreshTokenRepository;
import es.kitti.auth.security.InternalOnly;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalOnly
public class AuthInternalResource {

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @DELETE
    @Path("/tokens/user/{userId}")
    @WithTransaction
    public Uni<Response> deleteTokensByUser(@PathParam("userId") Long userId) {
        return refreshTokenRepository.deleteAllByUserId(userId)
                .onItem().transform(count -> Response.noContent().build());
    }

    @POST
    @Path("/purge/tokens")
    @WithTransaction
    public Uni<Response> purgeExpiredTokens() {
        return refreshTokenRepository.deleteExpiredOrRevoked()
                .onItem().transform(count -> Response.noContent().build());
    }
}