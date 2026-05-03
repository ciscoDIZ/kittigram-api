package es.kitti.schedule.job;

import es.kitti.schedule.client.UserInternalClient;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class UserErasurePurgeJob {

    @Inject
    @RestClient
    UserInternalClient client;

    @ConfigProperty(name = "kitties.internal.secret")
    String secret;

    @Scheduled(cron = "0 0 2 * * ?")
    Uni<Void> run() {
        Log.info("Triggering user erasure purge");
        return client.triggerErasurePurge(secret)
                .invoke(() -> Log.info("User erasure purge triggered"))
                .replaceWithVoid();
    }
}