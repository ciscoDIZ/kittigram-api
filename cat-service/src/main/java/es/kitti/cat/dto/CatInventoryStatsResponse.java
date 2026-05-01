package es.kitti.cat.dto;

public record CatInventoryStatsResponse(
        long available,
        long unavailable,
        long deleted,
        long total
) {}
