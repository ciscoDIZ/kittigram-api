package org.ciscoadiz.adoption.event;

public record AdoptionFormAnalysedEvent(
        Long adoptionRequestId,
        String decision,
        String rejectionReason,
        int criticalFlags,
        int warningFlags,
        int noticeFlags
) {}