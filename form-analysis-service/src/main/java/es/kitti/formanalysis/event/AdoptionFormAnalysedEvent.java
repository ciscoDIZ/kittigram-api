package es.kitti.formanalysis.event;

public record AdoptionFormAnalysedEvent(
        Long adoptionRequestId,
        String decision,
        String rejectionReason,
        Long adopterId,
        int criticalFlags,
        int warningFlags,
        int noticeFlags
) {}