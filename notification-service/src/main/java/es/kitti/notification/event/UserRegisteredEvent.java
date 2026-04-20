package es.kitti.notification.event;

public record UserRegisteredEvent(
        Long userId,
        String email,
        String name,
        String activationToken
) {}