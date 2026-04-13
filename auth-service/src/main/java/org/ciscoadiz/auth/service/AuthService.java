package org.ciscoadiz.auth.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.ciscoadiz.auth.dto.AuthRequest;
import org.ciscoadiz.auth.dto.AuthResponse;
import org.ciscoadiz.auth.dto.RefreshRequest;
import org.ciscoadiz.auth.entity.RefreshToken;
import org.ciscoadiz.auth.exception.InvalidCredentialsException;
import org.ciscoadiz.auth.exception.InvalidTokenException;
import org.ciscoadiz.auth.grpc.UserServiceClient;
import org.ciscoadiz.auth.repository.RefreshTokenRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class AuthService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @Inject
    UserServiceClient userServiceClient;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @WithTransaction
    public Uni<AuthResponse> authenticate(AuthRequest request) {
        return userServiceClient.validateCredentials(request.email(), request.password())
                .onItem().transformToUni(response -> {
                    if (!response.getValid()) {
                        return Uni.createFrom().failure(
                                new InvalidCredentialsException()
                        );
                    }
                    return generateTokens(response.getUserId(), response.getEmail());
                });
    }

    @WithTransaction
    public Uni<AuthResponse> refresh(RefreshRequest request) {
        Log.infof("Refresh request token: '%s'", request.refreshToken());
        return refreshTokenRepository.findByToken(request.refreshToken())
                .onItem().ifNull()
                .failWith(() -> new InvalidTokenException("Refresh token not found"))
                .onItem().transformToUni(token -> {
                    if (!token.isValid()) {
                        return Uni.createFrom().failure(
                                new InvalidTokenException("Refresh token expired or revoked")
                        );
                    }
                    return generateTokens(token.userId, token.email);
                });
    }

    @WithTransaction
    public Uni<Void> logout(String refreshToken) {
        return refreshTokenRepository.findByToken(refreshToken)
                .onItem().ifNull()
                .failWith(() -> new InvalidTokenException("Refresh token not found"))
                .onItem().transformToUni(token -> {
                    token.revoked = true;
                    return refreshTokenRepository.persist(token).replaceWithVoid();
                });
    }

    private Uni<AuthResponse> generateTokens(long userId, String email) {
        String accessToken = Jwt.issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("email", email)
                .expiresIn(900)
                .sign();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.token = UUID.randomUUID().toString();
        refreshToken.userId = userId;
        refreshToken.email = email;
        refreshToken.expiresAt = LocalDateTime.now().plusDays(7);

        return refreshTokenRepository.persist(refreshToken)
                .onItem().transform(saved -> new AuthResponse(
                        accessToken,
                        saved.token,
                        900
                ));
    }
}