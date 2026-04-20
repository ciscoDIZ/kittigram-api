package es.kitti.user.event;

public record UserRegisteredEvent(
        Long userId,
        String email,
        String name,
        String activationToken
) {}