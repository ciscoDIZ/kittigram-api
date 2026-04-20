package es.kitti.formanalysis.rules;

import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.formanalysis.entity.FlagSeverity;
import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class FormAnalysisRules {

    public List<FlagResult> evaluate(AdoptionFormSubmittedEvent event) {
        List<FlagResult> flags = new ArrayList<>();

        if (containsPhysicalPunishment(event.reactionToUnwantedBehavior())) {
            flags.add(new FlagResult(
                    FlagSeverity.Critical,
                    "PHYSICAL_PUNISHMENT",
                    "El adoptante menciona castigo físico como respuesta a comportamientos no deseados"
            ));
        }

        if (containsAbandonmentHistory(event.previousPetsHistory())) {
            flags.add(new FlagResult(
                    FlagSeverity.Critical,
                    "ABANDONMENT_HISTORY",
                    "El adoptante tiene historial de abandono de mascotas"
            ));
        }

        if (Boolean.TRUE.equals(event.isRental()) &&
                !Boolean.TRUE.equals(event.rentalPetsAllowed())) {
            flags.add(new FlagResult(
                    FlagSeverity.Critical,
                    "RENTAL_NO_PERMISSION",
                    "La vivienda es de alquiler y no tiene permiso explícito para mascotas"
            ));
        }

        if (Boolean.TRUE.equals(event.anyoneHasAllergies())) {
            flags.add(new FlagResult(
                    FlagSeverity.Critical,
                    "ALLERGY_CONFIRMED",
                    "Algún conviviente tiene alergia a los gatos"
            ));
        }

        if (event.dailyPlayMinutes() != null && event.dailyPlayMinutes() < 15) {
            flags.add(new FlagResult(
                    FlagSeverity.Warning,
                    "INSUFFICIENT_PLAY_TIME",
                    "Menos de 15 minutos de juego interactivo diario"
            ));
        }

        if (event.hoursAlonePerDay() != null && event.hoursAlonePerDay() > 10
                && !Boolean.TRUE.equals(event.hasOtherPets())) {
            flags.add(new FlagResult(
                    FlagSeverity.Warning,
                    "TOO_MANY_HOURS_ALONE",
                    "El gato estaría solo más de 10 horas al día sin otra mascota"
            ));
        }

        if (!Boolean.TRUE.equals(event.hasVerticalSpace()) &&
                !Boolean.TRUE.equals(event.hasHidingSpots())) {
            flags.add(new FlagResult(
                    FlagSeverity.Warning,
                    "NO_ENRICHMENT_SPACE",
                    "La vivienda no tiene alturas accesibles ni zonas de escondite"
            ));
        }

        if (Boolean.TRUE.equals(event.hasChildren()) &&
                !Boolean.TRUE.equals(event.hasPreviousCatExperience()) &&
                containsYoungChildren(event.childrenAges())) {
            flags.add(new FlagResult(
                    FlagSeverity.Warning,
                    "YOUNG_CHILDREN_NO_EXPERIENCE",
                    "Niños menores de 4 años en casa sin experiencia previa con gatos"
            ));
        }

        if (!Boolean.TRUE.equals(event.stableHousing())) {
            flags.add(new FlagResult(
                    FlagSeverity.Warning,
                    "UNSTABLE_HOUSING",
                    "La vivienda no es estable o hay mudanza prevista"
            ));
        }

        if (containsSuperficialMotivation(event.motivationToAdopt())) {
            flags.add(new FlagResult(
                    FlagSeverity.Warning,
                    "SUPERFICIAL_MOTIVATION",
                    "La motivación para adoptar parece superficial"
            ));
        }

        if (!Boolean.TRUE.equals(event.hasWindowsWithView())) {
            flags.add(new FlagResult(
                    FlagSeverity.Notice,
                    "NO_WINDOW_VIEW",
                    "Sin ventanas con vistas accesibles para el gato"
            ));
        }

        if (event.housingSize() != null && event.housingSize() < 40) {
            flags.add(new FlagResult(
                    FlagSeverity.Notice,
                    "SMALL_HOUSING",
                    "La vivienda tiene menos de 40m²"
            ));
        }

        if (!Boolean.TRUE.equals(event.hasPreviousCatExperience())) {
            flags.add(new FlagResult(
                    FlagSeverity.Notice,
                    "NO_PREVIOUS_EXPERIENCE",
                    "El adoptante no ha tenido gatos anteriormente"
            ));
        }

        if (!Boolean.TRUE.equals(event.hasScratchingPost())) {
            flags.add(new FlagResult(
                    FlagSeverity.Notice,
                    "NO_SCRATCHING_POST",
                    "No tiene rascador ni planes de comprar uno"
            ));
        }

        return flags;
    }

    private boolean containsPhysicalPunishment(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("pegar") || lower.contains("golpe") ||
                lower.contains("castigo físico") || lower.contains("bofetada") ||
                lower.contains("palo") || lower.contains("hit") ||
                lower.contains("smack") || lower.contains("beat");
    }

    private boolean containsAbandonmentHistory(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("abandoné") || lower.contains("abandone") ||
                lower.contains("tiré") || lower.contains("tire") ||
                lower.contains("solté") || lower.contains("solte") ||
                lower.contains("dejé en la calle") || lower.contains("deje en la calle");
    }

    private boolean containsYoungChildren(String childrenAges) {
        if (childrenAges == null) return false;
        return childrenAges.matches(".*\\b[0-3]\\b.*");
    }

    private boolean containsSuperficialMotivation(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("es bonito") || lower.contains("es mono") ||
                lower.contains("de regalo") || lower.contains("para los niños") ||
                lower.contains("me parece gracioso") || lower.contains("capricho");
    }
}