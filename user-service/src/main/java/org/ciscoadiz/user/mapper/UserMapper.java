package org.ciscoadiz.user.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.ciscoadiz.user.dto.UserCreateRequest;
import org.ciscoadiz.user.dto.UserResponse;
import org.ciscoadiz.user.dto.UserUpdateRequest;
import org.ciscoadiz.user.entity.User;
import org.ciscoadiz.user.entity.UserRole;
import org.ciscoadiz.user.entity.UserStatus;

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
