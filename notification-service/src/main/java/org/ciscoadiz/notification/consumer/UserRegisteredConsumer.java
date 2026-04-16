package org.ciscoadiz.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.ciscoadiz.notification.event.UserRegisteredEvent;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class UserRegisteredConsumer {

    @Inject
    ReactiveMailer mailer;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Location("emails/activation")
    Template activationTemplate;

    @Incoming("user-registered")
    public Uni<Void> onUserRegistered(String message) {
        try {
            UserRegisteredEvent event = objectMapper.readValue(message, UserRegisteredEvent.class);
            Log.infof("Sending activation email to %s", event.email());

            String activationUrl = "http://localhost:8080/api/users/activate?token=" + event.activationToken();

            String html = activationTemplate
                    .data("name", event.name())
                    .data("activationUrl", activationUrl)
                    .render();

            return mailer.send(
                    Mail.withHtml(
                            event.email(),
                            "Activa tu cuenta en Kittigram 🐱",
                            html
                    )
            );
        } catch (Exception e) {
            Log.errorf("Error processing user-registered event: %s", e.getMessage());
            return Uni.createFrom().voidItem();
        }
    }
}