package es.kitti.auth.service;

import io.smallrye.mutiny.Uni;
import es.kitti.auth.dto.AuthRequest;
import es.kitti.auth.dto.AuthResponse;
import es.kitti.auth.dto.RefreshRequest;
import es.kitti.auth.entity.RefreshToken;
import es.kitti.auth.exception.InvalidCredentialsException;
import es.kitti.auth.exception.InvalidTokenException;
import es.kitti.auth.grpc.UserServiceClient;
import es.kitti.auth.repository.RefreshTokenRepository;
import es.kitti.user.grpc.ValidateCredentialsResponse;
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
        validRefreshToken.email = "test@kitti.es";
        validRefreshToken.role = "User";
        validRefreshToken.expiresAt = LocalDateTime.now().plusDays(7);
        validRefreshToken.revoked = false;
    }

    @Test
    void authenticate_validCredentials_returnsTokens() {
        var request = new AuthRequest("test@kitti.es", "password123");

        when(userServiceClient.validateCredentials("test@kitti.es", "password123"))
                .thenReturn(Uni.createFrom().item(
                        ValidateCredentialsResponse.newBuilder()
                                .setValid(true)
                                .setUserId(1L)
                                .setEmail("test@kitti.es")
                                .setRole("User")
                                .build()
                ));
        when(jwtTokenService.generateAccessToken(1L, "test@kitti.es", "User"))
                .thenReturn("mocked-access-token");
        when(refreshTokenRepository.persist(any(RefreshToken.class)))
                .thenReturn(Uni.createFrom().item(validRefreshToken));

        var result = authService.authenticate(request)
                .await().indefinitely();

        assertNotNull(result);
        assertEquals("mocked-access-token", result.accessToken());
        assertNotNull(result.refreshToken());
        verify(jwtTokenService).generateAccessToken(1L, "test@kitti.es", "User");
    }

    @Test
    void authenticate_invalidCredentials_throwsInvalidCredentialsException() {
        var request = new AuthRequest("wrong@kitti.es", "wrongpass");

        when(userServiceClient.validateCredentials("wrong@kitti.es", "wrongpass"))
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
        when(jwtTokenService.generateAccessToken(1L, "test@kitti.es", "User"))
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