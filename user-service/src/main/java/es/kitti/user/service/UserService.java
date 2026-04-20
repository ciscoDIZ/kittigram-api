package es.kitti.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import es.kitti.user.domain.ActivationToken;
import es.kitti.user.domain.Email;
import es.kitti.user.dto.UserCreateRequest;
import es.kitti.user.dto.UserResponse;
import es.kitti.user.dto.UserUpdateRequest;
import es.kitti.user.entity.User;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.event.UserRegisteredEvent;
import es.kitti.user.exception.InvalidTokenException;
import es.kitti.user.exception.UserNotFoundException;
import es.kitti.user.mapper.UserMapper;
import es.kitti.user.repository.UserRepository;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.List;

@ApplicationScoped
public class UserService {

    @Inject
    UserRepository userRepository;
    @Inject
    UserMapper userMapper;
    @Inject
    @Channel("user-registered")
    Emitter<UserRegisteredEvent> userRegisteredEmitter;
    @Inject
    ObjectMapper objectMapper;

    @WithSession
    public Uni<UserResponse> findByEmail(String email) {
        return userRepository.findByEmail(Email.of(email).value())
                .onItem().ifNull()
                .failWith(() -> new UserNotFoundException(email))
                .onItem().transform(userMapper::toResponse);
    }

    public Multi<UserResponse> findAllActiveUsers() {
        return Panache.withSession(
                () -> userRepository.findAllActiveUsers().collect().asList()
        ).onItem().transformToMulti(list -> Multi.createFrom().iterable(list))
                .onItem().transform(userMapper::toResponse);
    }


    @WithTransaction
    public Uni<UserResponse> createUser(UserCreateRequest request) {
        Email email = Email.of(request.email());
        return userRepository.existsByEmail(email.value())
                .onItem().transformToUni(exists -> {
                    if (exists) {
                        return Uni.createFrom()
                                .failure(new IllegalArgumentException(email.value()));
                    }
                    var hashedPassword = BcryptUtil.bcryptHash(request.password());
                    var user = userMapper.toEntity(request, hashedPassword);
                    return userRepository.persist(user);
                })
                .onItem().transform(user -> {
                    userRegisteredEmitter.send(new UserRegisteredEvent(
                            user.id, user.email, user.name, user.activationToken
                    ));
                    return userMapper.toResponse(user);
                });
    }

    @WithTransaction
    public Uni<UserResponse> updateUser(String email, UserUpdateRequest request) {
        return userRepository.findByEmail(email)
                .onItem().ifNull().failWith(() -> new UserNotFoundException(email)).onItem().transformToUni(user -> {
                    userMapper.updateEntity(user, request);
                    return userRepository.persist(user);
                }).onItem().transform(user -> {
                    userRegisteredEmitter.send(new UserRegisteredEvent(
                            user.id,
                            user.email,
                            user.name,
                            user.activationToken
                    ));
                    return userMapper.toResponse(user);
                });

    }

    @WithTransaction
    public Uni<UserResponse> deactivateUser(String email) {
        return userRepository.findByEmail(email)
                .onItem().ifNull().failWith(() -> new UserNotFoundException(email)).onItem().transformToUni(user -> {
                    user.status = UserStatus.Inactive;
                    return userRepository.persist(user);
                }).onItem().transform(userMapper::toResponse);
    }

    @WithTransaction
    public Uni<UserResponse> activateUser(String email) {
        return userRepository.findByEmail(email).onItem().ifNull().failWith(() -> new UserNotFoundException(email)).onItem().transformToUni(user -> {
            user.status = UserStatus.Active;
            return userRepository.persist(user);
        }).onItem().transform(userMapper::toResponse);
    }

    @WithTransaction
    public Uni<UserResponse> activateByToken(String token) {
        ActivationToken activationToken = ActivationToken.of(token);
        return userRepository.findByActivationToken(activationToken.value())
                .onItem().ifNull()
                .failWith(() -> new InvalidTokenException("Invalid or expired activation token"))
                .onItem().transformToUni(user -> {
                    if (user.activationTokenExpiresAt != null &&
                            user.activationTokenExpiresAt.isBefore(java.time.LocalDateTime.now())) {
                        return Uni.createFrom().failure(new InvalidTokenException("Invalid or expired activation token"));
                    }
                    user.status = UserStatus.Active;
                    user.activationToken = null;
                    user.activationTokenExpiresAt = null;
                    return userRepository.persist(user);
                })
                .onItem().transform(userMapper::toResponse);
    }
}
