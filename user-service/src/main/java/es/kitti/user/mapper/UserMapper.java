package es.kitti.user.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.user.dto.UserCreateRequest;
import es.kitti.user.dto.UserResponse;
import es.kitti.user.dto.UserUpdateRequest;
import es.kitti.user.entity.User;
import es.kitti.user.entity.UserRole;
import es.kitti.user.entity.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class UserMapper {
    public User toEntity(UserCreateRequest request, String passwordHash) {
        User user = new User();
        user.email = request.email();
        user.passwordHash = passwordHash;
        user.name = request.name();
        user.surname = request.surname();
        user.birthdate = request.birthdate();
        user.status = UserStatus.Pending;
        user.role = request.role() != null ? request.role() : UserRole.User;
        user.activationToken = UUID.randomUUID().toString();
        user.activationTokenExpiresAt = LocalDateTime.now().plusHours(24);
        return user;
    }

    public void updateEntity(User user, UserUpdateRequest request) {
        user.name = request.name();
        user.surname = request.surname();
        user.birthdate = request.birthdate();
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.id,
                user.email,
                user.name,
                user.surname,
                user.status,
                user.role,
                user.birthdate,
                user.createdAt,
                user.updatedAt
        );
    }
}
