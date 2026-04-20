package es.kitti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import es.kitti.notification.event.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegisteredConsumerUnitTest {

    @Mock
    ReactiveMailer mailer;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    Template activationTemplate;

    @Mock
    TemplateInstance templateInstance;

    @InjectMocks
    UserRegisteredConsumer consumer;

    @Test
    void onUserRegistered_validMessage_sendsEmail() throws Exception {
        var event = new UserRegisteredEvent(1L, "test@kitti.es", "Test", "token-123");
        var json = new ObjectMapper().writeValueAsString(event);

        when(objectMapper.readValue(json, UserRegisteredEvent.class)).thenReturn(event);
        when(activationTemplate.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.render()).thenReturn("<html>token-123</html>");
        when(mailer.send(any(Mail.class))).thenReturn(Uni.createFrom().voidItem());

        consumer.onUserRegistered(json).await().indefinitely();

        verify(mailer).send(any(Mail.class));
    }

    @Test
    void onUserRegistered_validMessage_sendsToCorrectRecipient() throws Exception {
        var event = new UserRegisteredEvent(1L, "test@kitti.es", "Test", "token-123");
        var json = new ObjectMapper().writeValueAsString(event);

        when(objectMapper.readValue(json, UserRegisteredEvent.class)).thenReturn(event);
        when(activationTemplate.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.data(anyString(), any())).thenReturn(templateInstance);
        when(templateInstance.render()).thenReturn("<html>token-123</html>");
        when(mailer.send(any(Mail.class))).thenReturn(Uni.createFrom().voidItem());

        consumer.onUserRegistered(json).await().indefinitely();

        ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
        verify(mailer).send(captor.capture());
        assertEquals("test@kitti.es", captor.getValue().getTo().get(0));
        assertEquals("Activa tu cuenta en Kittigram 🐱", captor.getValue().getSubject());
        assertTrue(captor.getValue().getHtml().contains("token-123"));
    }

    @Test
    void onUserRegistered_invalidJson_returnsVoidWithoutSendingEmail() throws Exception {
        when(objectMapper.readValue("invalid-json", UserRegisteredEvent.class))
                .thenThrow(new RuntimeException("Parse error"));

        var result = consumer.onUserRegistered("invalid-json")
                .await().indefinitely();

        assertNull(result);
        verify(mailer, never()).send(any(Mail.class));
    }
}