package es.kitti.chat.dto;

import jakarta.validation.constraints.Size;

public record BlockUserRequest(
        @Size(max = 500) String reason
) {}
