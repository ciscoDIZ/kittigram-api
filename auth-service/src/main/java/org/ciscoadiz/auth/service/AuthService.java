package org.ciscoadiz.auth.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
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

    @Inject
    JwtTokenService jwtTokenService;

    @WithTransaction
    public Uni<AuthResponse> authenticate(AuthRequest request) {
        return userServiceClient.validateCredentials(request.email(), request.password())
                .onItem().transformToUni(response -> {
                    if (!response.getValid()) {
                        return Uni.createFrom().failure(
                                new InvalidCredentialsException()
                        );
                    }
                    return generateTokens(response.getUserId(), response.getEmail(), response.getRole());
                });
    }

    @WithTransaction
    public Uni<AuthResponse> refresh(RefreshRequest request) {
        return refreshTokenRepository.findByToken(request.refreshToken())
                .onItem().ifNull()
                .failWith(() -> new InvalidTokenException("Refresh token not found"))
                .onItem().transformToUni(token -> {
                    if (!token.isValid()) {
                        return Uni.createFrom().failure(
                                new InvalidTokenException("Refresh token expired or revoked")
                        );
                    }
                    token.revoked = true;
                    return refreshTokenRepository.persist(token)
                            .onItem().transformToUni(t -> generateTokens(t.userId, t.email, t.role));
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

    private Uni<AuthResponse> generateTokens(long userId, String email, String role) {
        String accessToken = jwtTokenService.generateAccessToken(userId, email, role);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.token = UUID.randomUUID().toString();
        refreshToken.userId = userId;
        refreshToken.email = email;
        refreshToken.role = role;
        refreshToken.expiresAt = LocalDateTime.now().plusDays(7);

        return refreshTokenRepository.persist(refreshToken)
                .onItem().transform(saved -> new AuthResponse(
                        accessToken,
                        saved.token,
                        900
                ));
    }
}