package es.kitti.adoption.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.Map;

public class KafkaTestResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        Map<String, String> env = InMemoryConnector.switchOutgoingChannelsToInMemory(
                "adoption-form-submitted"
        );
        env.putAll(InMemoryConnector.switchIncomingChannelsToInMemory(
                "adoption-form-analysed"
        ));
        return env;
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}