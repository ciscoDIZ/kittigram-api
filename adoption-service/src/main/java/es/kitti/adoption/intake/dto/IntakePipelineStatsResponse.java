package es.kitti.adoption.intake.dto;

public record IntakePipelineStatsResponse(
        long pending,
        long approved,
        long rejected
) {}
