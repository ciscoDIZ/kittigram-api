package es.kitti.formanalysis.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.Map;

public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        Map<String, String> env = InMemoryConnector.switchIncomingChannelsToInMemory(
                "adoption-form-submitted"
        );
        env.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(
                "adoption-form-analysed"
        ));
        return env;
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}