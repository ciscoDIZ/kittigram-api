package org.ciscoadiz.auth.service;

import io.smallrye.mutiny.Uni;
import org.ciscoadiz.auth.dto.AuthRequest;
import org.ciscoadiz.auth.dto.AuthResponse;
import org.ciscoadiz.auth.dto.RefreshRequest;
import org.ciscoadiz.auth.entity.RefreshToken;
import org.ciscoadiz.auth.exception.InvalidCredentialsException;
import org.ciscoadiz.auth.exception.InvalidTokenException;
import org.ciscoadiz.auth.grpc.UserServiceClient;
import org.ciscoadiz.auth.repository.RefreshTokenRepository;
import org.ciscoadiz.user.grpc.ValidateCredentialsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserServiceClient userServiceClient;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    JwtTokenService jwtTokenService;

    @InjectMocks
    AuthService authService;

    private RefreshToken validRefreshToken;

    @BeforeEach
    void setUp() {
        validRefreshToken = new RefreshToken();
        validRefreshToken.id = 1L;
        validRefreshToken.token = UUID.randomUUID().toString();
        validRefreshToken.userId = 1L;
        validRefreshToken.email = "test@kittigram.org";
        validRefreshToken.role = "User";
        validRefreshToken.expiresAt = LocalDateTime.now().plusDays(7);
        validRefreshToken.revoked = false;
    }

    @Test
    void authenticate_validCredentials_returnsTokens() {
        var request = new AuthRequest("test@kittigram.org", "password123");

        when(userServiceClient.validateCredentials("test@kittigram.org", "password123"))
                .thenReturn(Uni.createFrom().item(
                        ValidateCredentialsResponse.newBuilder()
                                .setValid(true)
                                .setUserId(1L)
                                .setEmail("test@kittigram.org")
                                .setRole("User")
                                .build()
                ));
        when(jwtTokenService.generateAccessToken(1L, "test@kittigram.org", "User"))
                .thenReturn("mocked-access-token");
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.authenticate(request)
                .await().indefinitely();

        assertNotNull(result);
        assertEquals("mocked-access-token", result.accessToken());
        assertNotNull(result.refreshToken());
        verify(jwtTokenService).generateAccessToken(1L, "test@kittigram.org", "User");
    }

    @Test
    void authenticate_invalidCredentials_throwsInvalidCredentialsException() {
        var request = new AuthRequest("wrong@kittigram.org", "wrongpass");

        when(userServiceClient.validateCredentials("wrong@kittigram.org", "wrongpass"))
                .thenReturn(Uni.createFrom().item(
                        ValidateCredentialsResponse.newBuilder()
                                .setValid(false)
                                .build()
                ));

        assertThrows(InvalidCredentialsException.class, () ->
                authService.authenticate(request)
                        .await().indefinitely()
        );
        verify(refreshTokenRepository, never()).persist(any(RefreshToken.class));
    }

    @Test
    void refresh_validToken_returnsNewTokens() {
        var request = new RefreshRequest(validRefreshToken.token);

        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));
        when(jwtTokenService.generateAccessToken(1L, "test@kittigram.org", "User"))
                .thenReturn("new-access-token");
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.refresh(request)
                .await().indefinitely();

        assertNotNull(result);
        assertEquals("new-access-token", result.accessToken());
    }

    @Test
    void refresh_tokenNotFound_throwsInvalidTokenException() {
        var request = new RefreshRequest("nonexistent-token");

        when(refreshTokenRepository.findByToken("nonexistent-token"))
                .thenReturn(Uni.createFrom().nullItem());

        assertThrows(InvalidTokenException.class, () ->
                authService.refresh(request)
                        .await().indefinitely()
        );
    }

    @Test
    void refresh_expiredToken_throwsInvalidTokenException() {
        validRefreshToken.expiresAt = LocalDateTime.now().minusDays(1);
        var request = new RefreshRequest(validRefreshToken.token);

        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        assertThrows(InvalidTokenException.class, () ->
                authService.refresh(request)
                        .await().indefinitely()
        );
    }

    @Test
    void refresh_revokedToken_throwsInvalidTokenException() {
        validRefreshToken.revoked = true;
        var request = new RefreshRequest(validRefreshToken.token);

        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        assertThrows(InvalidTokenException.class, () ->
                authService.refresh(request)
                        .await().indefinitely()
        );
    }

    @Test
    void logout_validToken_revokesToken() {
        when(refreshTokenRepository.findByToken(validRefreshToken.token))
                .thenReturn(Uni.createFrom().item(validRefreshToken));
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        authService.logout(validRefreshToken.token)
                .await().indefinitely();

        assertTrue(validRefreshToken.revoked);
        verify(refreshTokenRepository).persist(validRefreshToken);
    }

    @Test
    void logout_tokenNotFound_throwsInvalidTokenException() {
        when(refreshTokenRepository.findByToken("nonexistent-token"))
                .thenReturn(Uni.createFrom().nullItem());

        assertThrows(InvalidTokenException.class, () ->
                authService.logout("nonexistent-token")
                        .await().indefinitely()
        );
    }
}