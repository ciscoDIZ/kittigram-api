package es.kitti.adoption.dto;

public record AdoptionPipelineStatsResponse(
        long pending,
        long reviewing,
        long accepted,
        long formCompleted,
        long paymentPending,
        long paymentFailed,
        long completed,
        long rejected
) {}
