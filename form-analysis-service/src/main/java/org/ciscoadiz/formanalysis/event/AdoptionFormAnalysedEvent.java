package org.ciscoadiz.formanalysis.event;

public record AdoptionFormAnalysedEvent(
        Long adoptionRequestId,
        String decision,
        String rejectionReason,
        int criticalFlags,
        int warningFlags,
        int noticeFlags
) {}