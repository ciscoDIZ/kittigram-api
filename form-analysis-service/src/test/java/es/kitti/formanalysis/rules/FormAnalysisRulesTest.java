package es.kitti.formanalysis.rules;

import es.kitti.formanalysis.entity.FlagSeverity;
import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FormAnalysisRulesTest {

    @InjectMocks
    FormAnalysisRules formAnalysisRules;

    private AdoptionFormSubmittedEvent buildCleanEvent() {
        return new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L, "adopter@kitti.es",
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "Los gatos necesitan cazar por instinto aunque tengan comida",
                30, "Caña, ratones, túneles",
                "Ignorar y redirigir con juguetes",
                true, true,
                "Quiero dar un hogar a un gato que lo necesita",
                true, true, true, false, null
        );
    }

    @Test
    void cleanForm_noFlags() {
        var flags = formAnalysisRules.evaluate(buildCleanEvent());
        assertTrue(flags.isEmpty());
    }

    @Test
    void physicalPunishment_triggersCriticalFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L, "adopter@kitti.es",
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes",
                "le pegaría para que aprenda",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Critical
                        && f.code().equals("PHYSICAL_PUNISHMENT")));
    }

    @Test
    void rentalWithoutPermission_triggersCriticalFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L, "adopter@kitti.es",
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, true, false,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Critical
                        && f.code().equals("RENTAL_NO_PERMISSION")));
    }

    @Test
    void allergyConfirmed_triggersCriticalFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L, "adopter@kitti.es",
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, true, "alergia leve"
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Critical
                        && f.code().equals("ALLERGY_CONFIRMED")));
    }

    @Test
    void insufficientPlayTime_triggersWarningFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L, "adopter@kitti.es",
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 10, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Warning
                        && f.code().equals("INSUFFICIENT_PLAY_TIME")));
    }

    @Test
    void noEnrichmentSpace_triggersWarningFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L, "adopter@kitti.es",
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, false, false, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Warning
                        && f.code().equals("NO_ENRICHMENT_SPACE")));
    }

    @Test
    void smallHousing_triggersNoticeFlag() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L, "adopter@kitti.es",
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 30, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes", "ignorar",
                true, true, "amor", true, true, true, false, null
        );

        var flags = formAnalysisRules.evaluate(event);

        assertTrue(flags.stream()
                .anyMatch(f -> f.severity() == FlagSeverity.Notice
                        && f.code().equals("SMALL_HOUSING")));
    }

    @Test
    void multipleCriticalFlags_allDetected() {
        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L, "adopter@kitti.es",
                true, "abandoné a mi perro", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, true, false,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes",
                "le pegaría",
                true, true, "amor", true, true, true, true, "alergia"
        );

        var flags = formAnalysisRules.evaluate(event);

        long criticalCount = flags.stream()
                .filter(f -> f.severity() == FlagSeverity.Critical)
                .count();

        assertTrue(criticalCount >= 3);
    }
}