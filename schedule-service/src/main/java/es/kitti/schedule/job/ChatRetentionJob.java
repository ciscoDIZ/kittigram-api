package es.kitti.schedule.job;

import es.kitti.schedule.client.ChatInternalClient;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class ChatRetentionJob {

    @Inject
    @RestClient
    ChatInternalClient client;

    @ConfigProperty(name = "kitties.internal.secret")
    String secret;

    @Scheduled(cron = "0 0 4 * * ?")
    Uni<Void> run() {
        Log.info("Triggering chat data retention run");
        return client.triggerRetention(secret)
                .invoke(() -> Log.info("Chat retention run triggered"))
                .replaceWithVoid();
    }
}