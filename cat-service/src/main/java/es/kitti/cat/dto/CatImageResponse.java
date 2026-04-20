package es.kitti.cat.dto;

public record CatImageResponse(
        Long id,
        String url,
        Integer order
) {}