package es.kitti.storage.dto;

public record UploadResponse(
        String key,
        String url
) {}