package es.kitti.formanalysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import es.kitti.formanalysis.entity.*;
import es.kitti.formanalysis.event.AdoptionFormAnalysedEvent;
import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;
import es.kitti.formanalysis.repository.FormAnalysisRepository;
import es.kitti.formanalysis.repository.FormFlagRepository;
import es.kitti.formanalysis.rules.FlagResult;
import es.kitti.formanalysis.rules.FormAnalysisRules;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.List;

@ApplicationScoped
public class FormAnalysisService {

    @Inject
    FormAnalysisRepository formAnalysisRepository;

    @Inject
    FormFlagRepository formFlagRepository;

    @Inject
    FormAnalysisRules formAnalysisRules;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Channel("adoption-form-analysed")
    Emitter<AdoptionFormAnalysedEvent> adoptionFormAnalysedEmitter;

    @Inject
    FormAnalysisPersistenceService persistenceService;

    @Incoming("adoption-form-submitted")
    public Uni<Void> onFormSubmitted(String message) {
        try {
            AdoptionFormSubmittedEvent event = objectMapper.readValue(
                    message, AdoptionFormSubmittedEvent.class);

            Log.infof("Analysing form for adoption request: %d", event.adoptionRequestId());

            List<FlagResult> flags = formAnalysisRules.evaluate(event);

            long criticalCount = flags.stream()
                    .filter(f -> f.severity() == FlagSeverity.Critical).count();
            long warningCount = flags.stream()
                    .filter(f -> f.severity() == FlagSeverity.Warning).count();
            long noticeCount = flags.stream()
                    .filter(f -> f.severity() == FlagSeverity.Notice).count();

            AnalysisDecision decision = determineDecision(criticalCount, warningCount);
            String rejectionReason = buildRejectionReason(flags, decision);

            FormAnalysis analysis = new FormAnalysis();
            analysis.adoptionRequestId = event.adoptionRequestId();
            analysis.decision = decision;
            analysis.rejectionReason = rejectionReason;
            analysis.criticalFlags = (int) criticalCount;
            analysis.warningFlags = (int) warningCount;
            analysis.noticeFlags = (int) noticeCount;

            List<FormFlag> formFlags = flags.stream().map(f -> {
                FormFlag flag = new FormFlag();
                flag.severity = f.severity();
                flag.code = f.code();
                flag.description = f.description();
                return flag;
            }).toList();

            return persistenceService.persist(analysis, formFlags)
                    .onItem().invoke(saved -> {
                        adoptionFormAnalysedEmitter.send(new AdoptionFormAnalysedEvent(
                                event.adoptionRequestId(),
                                decision.name(),
                                rejectionReason,
                                event.adopterEmail(),
                                (int) criticalCount,
                                (int) warningCount,
                                (int) noticeCount
                        ));
                        Log.infof("Form analysis completed for request %d: %s",
                                event.adoptionRequestId(), decision);
                    })
                    .replaceWithVoid();

        } catch (Exception e) {
            Log.errorf("Error processing adoption-form-submitted: %s", e.getMessage());
            return Uni.createFrom().voidItem();
        }
    }

    private AnalysisDecision determineDecision(long criticalCount, long warningCount) {
        if (criticalCount >= 1) return AnalysisDecision.Rejected;
        if (warningCount >= 3) return AnalysisDecision.Rejected;
        if (warningCount >= 1) return AnalysisDecision.ReviewRequired;
        return AnalysisDecision.Approved;
    }

    private String buildRejectionReason(List<FlagResult> flags, AnalysisDecision decision) {
        if (decision == AnalysisDecision.Approved) return null;

        return flags.stream()
                .filter(f -> f.severity() == FlagSeverity.Critical ||
                        f.severity() == FlagSeverity.Warning)
                .map(FlagResult::description)
                .reduce((a, b) -> a + ". " + b)
                .orElse(null);
    }
}