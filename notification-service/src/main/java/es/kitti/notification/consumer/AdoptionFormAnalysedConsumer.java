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
import es.kitti.notification.client.UserServiceClient;
import es.kitti.notification.client.UserServiceClient.UserSummary;
import es.kitti.notification.event.AdoptionFormAnalysedEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class AdoptionFormAnalysedConsumer {

    @Inject
    ReactiveMailer mailer;

    @Inject
    ObjectMapper objectMapper;

    @RestClient
    UserServiceClient userServiceClient;

    @ConfigProperty(name = "kitties.internal.secret")
    String internalSecret;

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

            return userServiceClient.findById(event.adopterId(), internalSecret)
                    .onItem().transformToUni(user -> switch (event.decision()) {
                        case "Rejected" -> sendRejectionEmail(event, user);
                        case "ReviewRequired" -> sendReviewRequiredEmail(user);
                        case "Approved" -> sendApprovedEmail(user);
                        default -> {
                            Log.warnf("Unknown decision: %s", event.decision());
                            yield Uni.createFrom().voidItem();
                        }
                    })
                    .onFailure().invoke(e ->
                            Log.errorf("Failed to send notification for request %d: %s",
                                    event.adoptionRequestId(), e.getMessage()))
                    .onFailure().recoverWithNull()
                    .replaceWithVoid();

        } catch (Exception e) {
            Log.errorf("Error processing adoption-form-analysed event: %s", e.getMessage());
            return Uni.createFrom().voidItem();
        }
    }

    private Uni<Void> sendRejectionEmail(AdoptionFormAnalysedEvent event, UserSummary user) {
        String html = rejectionTemplate
                .data("rejectionReason", event.rejectionReason())
                .render();

        return mailer.send(
                Mail.withHtml(
                        user.email(),
                        "Actualización sobre tu solicitud de adopción en Kitties 🐱",
                        html
                )
        );
    }

    private Uni<Void> sendReviewRequiredEmail(UserSummary user) {
        String html = reviewRequiredTemplate.render();

        return mailer.send(
                Mail.withHtml(
                        user.email(),
                        "Tu solicitud está siendo revisada en Kitties 🐱",
                        html
                )
        );
    }

    private Uni<Void> sendApprovedEmail(UserSummary user) {
        String html = approvedTemplate.render();

        return mailer.send(
                Mail.withHtml(
                        user.email(),
                        "¡Buenas noticias sobre tu solicitud en Kitties! 🐱",
                        html
                )
        );
    }
}
