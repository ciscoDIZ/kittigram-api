package es.kitti.notification.event;

public record AdoptionFormAnalysedEvent(
        Long adoptionRequestId,
        String decision,
        String rejectionReason,
        int criticalFlags,
        int warningFlags,
        int noticeFlags,
        String adopterEmail
) {}