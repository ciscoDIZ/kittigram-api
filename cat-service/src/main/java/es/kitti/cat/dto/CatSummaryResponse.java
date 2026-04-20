package es.kitti.cat.dto;

import java.time.LocalDateTime;

public record CatSummaryResponse(
        Long id,
        String name,
        String profileImageUrl,
        Integer age,
        String sex,
        Boolean neutered,
        String status,
        String city,
        String region,
        String country,
        LocalDateTime createdAt
) {}