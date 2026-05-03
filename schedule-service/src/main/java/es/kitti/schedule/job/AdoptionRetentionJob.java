package es.kitti.schedule.job;

import es.kitti.schedule.client.AdoptionInternalClient;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class AdoptionRetentionJob {

    @Inject
    @RestClient
    AdoptionInternalClient client;

    @ConfigProperty(name = "kitties.internal.secret")
    String secret;

    @Scheduled(cron = "0 30 2 * * ?")
    Uni<Void> run() {
        Log.info("Triggering adoption data retention run");
        return client.triggerRetention(secret)
                .invoke(() -> Log.info("Adoption retention run triggered"))
                .replaceWithVoid();
    }
}
