package es.kitti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import es.kitti.notification.event.AdoptionFormAnalysedEvent;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class AdoptionFormAnalysedConsumer {

    @Inject
    ReactiveMailer mailer;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Location("emails/adoption-rejected")
    Template rejectionTemplate;

    @Inject
    @Location("emails/adoption-review-required")
    Template reviewRequiredTemplate;

    @Inject
    @Location("emails/adoption-approved")
    Template approvedTemplate;

    @Incoming("adoption-form-analysed")
    public Uni<Void> onFormAnalysed(String message) {
        try {
            AdoptionFormAnalysedEvent event = objectMapper.readValue(
                    message, AdoptionFormAnalysedEvent.class);

            Log.infof("Processing adoption-form-analysed for request: %d, decision: %s",
                    event.adoptionRequestId(), event.decision());

            return switch (event.decision()) {
                case "Rejected" -> sendRejectionEmail(event);
                case "ReviewRequired" -> sendReviewRequiredEmail(event);
                case "Approved" -> sendApprovedEmail(event);
                default -> {
                    Log.warnf("Unknown decision: %s", event.decision());
                    yield Uni.createFrom().voidItem();
                }
            };

        } catch (Exception e) {
            Log.errorf("Error processing adoption-form-analysed event: %s", e.getMessage());
            return Uni.createFrom().voidItem();
        }
    }

    private Uni<Void> sendRejectionEmail(AdoptionFormAnalysedEvent event) {
        String html = rejectionTemplate
                .data("rejectionReason", event.rejectionReason())
                .render();

        return mailer.send(
                Mail.withHtml(
                        event.adopterEmail(),
                        "Actualización sobre tu solicitud de adopción en Kittigram 🐱",
                        html
                )
        );
    }

    private Uni<Void> sendReviewRequiredEmail(AdoptionFormAnalysedEvent event) {
        String html = reviewRequiredTemplate.render();

        return mailer.send(
                Mail.withHtml(
                        event.adopterEmail(),
                        "Tu solicitud está siendo revisada en Kittigram 🐱",
                        html
                )
        );
    }

    private Uni<Void> sendApprovedEmail(AdoptionFormAnalysedEvent event) {
        String html = approvedTemplate.render();

        return mailer.send(
                Mail.withHtml(
                        event.adopterEmail(),
                        "¡Buenas noticias sobre tu solicitud en Kittigram! 🐱",
                        html
                )
        );
    }
}