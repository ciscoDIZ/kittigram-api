package es.kitti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import es.kitti.notification.event.UserRegisteredEvent;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserRegisteredConsumerTest {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Inject
    MockMailbox mailbox;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mailbox.clear();
    }

    @Test
    void testActivationEmailSent() throws Exception {
        InMemorySource<String> source = connector.source("user-registered");

        String payload = objectMapper.writeValueAsString(new UserRegisteredEvent(
                1L,
                "test@kitti.es",
                "Test",
                "test-activation-token"
        ));

        source.send(payload);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(mailbox.getMailsSentTo("test@kitti.es").isEmpty());
            var mail = mailbox.getMailsSentTo("test@kitti.es").get(0);
            assertEquals("Activa tu cuenta en Kittigram 🐱", mail.getSubject());
            assertTrue(mail.getHtml().contains("test-activation-token"));
        });
    }

    @Test
    void testActivationEmailContainsActivationLink() throws Exception {
        InMemorySource<String> source = connector.source("user-registered");

        String payload = objectMapper.writeValueAsString(new UserRegisteredEvent(
                2L,
                "link-test@kitti.es",
                "LinkTest",
                "my-unique-token-123"
        ));

        source.send(payload);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(mailbox.getMailsSentTo("link-test@kitti.es").isEmpty());
            var mail = mailbox.getMailsSentTo("link-test@kitti.es").get(0);
            assertTrue(mail.getHtml().contains("activate?token=my-unique-token-123"));
        });
    }
}