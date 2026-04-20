package es.kitti.user.service;

import io.smallrye.mutiny.Uni;
import es.kitti.user.dto.UserCreateRequest;
import es.kitti.user.dto.UserResponse;
import es.kitti.user.entity.User;
import es.kitti.user.entity.UserRole;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.event.UserRegisteredEvent;
import es.kitti.user.exception.InvalidTokenException;
import es.kitti.user.exception.UserNotFoundException;
import es.kitti.user.mapper.UserMapper;
import es.kitti.user.repository.UserRepository;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    UserMapper userMapper;

    @Mock
    Emitter<UserRegisteredEvent> userRegisteredEmitter;

    @InjectMocks
    UserService userService;

    private User testUser;
    private UserResponse testUserResponse;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.id = 1L;
        testUser.email = "test@kitti.es";
        testUser.name = "Test";
        testUser.surname = "User";
        testUser.status = UserStatus.Pending;
        testUser.activationToken = "valid-token-123";

        testUserResponse = new UserResponse(
                1L, "test@kitti.es", "Test", "User",
                UserStatus.Pending, UserRole.User,null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void createUser_success() {
        var request = new UserCreateRequest(
                "test@kitti.es",
                "password123",
                "Test",
                "User",
                null,
                null,
                UserRole.User
        );

        when(userRepository.existsByEmail(request.email()))
                .thenReturn(Uni.createFrom().item(false));
        when(userRepository.persist(any(User.class)))
                .thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toEntity(any(), anyString()))
                .thenReturn(testUser);
        when(userMapper.toResponse(testUser))
                .thenReturn(testUserResponse);

        var result = userService.createUser(request)
                .await().indefinitely();

        assertNotNull(result);
        assertEquals("test@kitti.es", result.email());
        assertEquals(UserStatus.Pending, result.status());
        verify(userRegisteredEmitter).send(any(UserRegisteredEvent.class));
    }

    @Test
    void createUser_duplicateEmail_throwsIllegalArgumentException() {
        var request = new UserCreateRequest(
                "duplicate@kitti.es", "password123", "Test", "User", null, null, UserRole.User
        );

        when(userRepository.existsByEmail(request.email()))
                .thenReturn(Uni.createFrom().item(true));

        assertThrows(IllegalArgumentException.class, () ->
                userService.createUser(request)
                        .await().indefinitely()
        );
        verify(userRegisteredEmitter, never()).send(any(UserRegisteredEvent.class));
    }

    @Test
    void findByEmail_userExists_returnsResponse() {
        when(userRepository.findByEmail("test@kitti.es"))
                .thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser))
                .thenReturn(testUserResponse);

        var result = userService.findByEmail("test@kitti.es")
                .await().indefinitely();

        assertNotNull(result);
        assertEquals("test@kitti.es", result.email());
    }

    @Test
    void findByEmail_userNotFound_throwsUserNotFoundException() {
        when(userRepository.findByEmail("nonexistent@kitti.es"))
                .thenReturn(Uni.createFrom().nullItem());

        assertThrows(UserNotFoundException.class, () ->
                userService.findByEmail("nonexistent@kitti.es")
                        .await().indefinitely()
        );
    }

    @Test
    void activateByToken_validToken_activatesUser() {
        when(userRepository.findByActivationToken("valid-token-123"))
                .thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class)))
                .thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser))
                .thenReturn(new UserResponse(
                        1L, "test@kitti.es", "Test", "User",
                        UserStatus.Active, UserRole.User,null,
                        LocalDateTime.now(),LocalDateTime.now()
                ));

        var result = userService.activateByToken("valid-token-123")
                .await().indefinitely();

        assertEquals(UserStatus.Active, result.status());
        assertNull(testUser.activationToken);
    }

    @Test
    void activateByToken_invalidToken_throwsInvalidTokenException() {
        when(userRepository.findByActivationToken("invalid-token"))
                .thenReturn(Uni.createFrom().nullItem());

        assertThrows(InvalidTokenException.class, () ->
                userService.activateByToken("invalid-token")
                        .await().indefinitely()
        );
    }

    @Test
    void deactivateUser_userExists_setsInactiveStatus() {
        when(userRepository.findByEmail("test@kitti.es"))
                .thenReturn(Uni.createFrom().item(testUser));
        when(userRepository.persist(any(User.class)))
                .thenReturn(Uni.createFrom().item(testUser));
        when(userMapper.toResponse(testUser))
                .thenReturn(new UserResponse(
                        1L, "test@kitti.es", "Test", "User",
                        UserStatus.Inactive, UserRole.User,null,
                        LocalDateTime.now(), LocalDateTime.now()
                ));

        var result = userService.deactivateUser("test@kitti.es")
                .await().indefinitely();

        assertEquals(UserStatus.Inactive, result.status());
        assertEquals(UserStatus.Inactive, testUser.status);
    }

    @Test
    void deactivateUser_userNotFound_throwsUserNotFoundException() {
        when(userRepository.findByEmail("nonexistent@kitti.es"))
                .thenReturn(Uni.createFrom().nullItem());

        assertThrows(UserNotFoundException.class, () ->
                userService.deactivateUser("nonexistent@kitti.es")
                        .await().indefinitely()
        );
    }
}