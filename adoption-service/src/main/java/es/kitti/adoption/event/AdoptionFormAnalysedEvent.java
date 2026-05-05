package es.kitti.adoption.event;

public record AdoptionFormAnalysedEvent(
        Long adoptionRequestId,
        String decision,
        String rejectionReason,
        Long adopterId,
        int criticalFlags,
        int warningFlags,
        int noticeFlags
) {}