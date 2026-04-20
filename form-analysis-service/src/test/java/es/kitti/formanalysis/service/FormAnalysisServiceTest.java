package es.kitti.formanalysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import es.kitti.formanalysis.entity.AnalysisDecision;
import es.kitti.formanalysis.entity.FormAnalysis;
import es.kitti.formanalysis.entity.FormFlag;
import es.kitti.formanalysis.event.AdoptionFormAnalysedEvent;
import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;
import es.kitti.formanalysis.test.KafkaTestResource;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
class FormAnalysisServiceTest {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Inject
    ObjectMapper objectMapper;

    @InjectMock
    FormAnalysisPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");
        sink.clear();

        FormAnalysis savedAnalysis = new FormAnalysis();
        savedAnalysis.id = 1L;

        when(persistenceService.persist(any(FormAnalysis.class), any()))
                .thenReturn(Uni.createFrom().item(savedAnalysis));
    }

    @Test
    void cleanForm_emitsApprovedDecision() throws Exception {
        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");

        var event = new AdoptionFormSubmittedEvent(
                1L, 10L, 100L, 200L, "adopter@kitti.es",
                true, "Murió de vejez", 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "Los gatos necesitan cazar por instinto",
                30, "Caña, ratones, túneles",
                "Ignorar y redirigir con juguetes",
                true, true,
                "Quiero dar un hogar a un gato",
                true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(sink.received().isEmpty());
            var analysed = sink.received().get(0).getPayload();
            assertEquals(AnalysisDecision.Approved.name(), analysed.decision());
            assertEquals(0, analysed.criticalFlags());
        });
    }

    @Test
    void formWithCriticalFlag_emitsRejectedDecision() throws Exception {
        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");

        var event = new AdoptionFormSubmittedEvent(
                2L, 10L, 100L, 200L, "adopter@kitti.es",
                true, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, true, true, "Quiet",
                "instinto", 30, "juguetes",
                "le pegaría para que aprenda",
                true, true, "amor", true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(sink.received().isEmpty());
            var analysed = sink.received().get(0).getPayload();
            assertEquals(AnalysisDecision.Rejected.name(), analysed.decision());
            assertTrue(analysed.criticalFlags() >= 1);
        });
    }

    @Test
    void formWithWarningFlags_emitsReviewRequiredDecision() throws Exception {
        InMemorySource<String> source = connector.source("adoption-form-submitted");
        InMemorySink<AdoptionFormAnalysedEvent> sink = connector.sink("adoption-form-analysed");

        var event = new AdoptionFormSubmittedEvent(
                3L, 10L, 100L, 200L, "adopter@kitti.es",
                false, null, 2, false, null,
                false, null, 8, true, null,
                "Apartment", 70, false, false, null,
                true, false, false, "Quiet",
                "instinto", 10, "juguetes",
                "ignorar",
                false, true, "amor", true, true, true, false, null
        );

        source.send(objectMapper.writeValueAsString(event));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertFalse(sink.received().isEmpty());
            var analysed = sink.received().get(0).getPayload();
            assertNotEquals(AnalysisDecision.Approved.name(), analysed.decision());
        });
    }
}