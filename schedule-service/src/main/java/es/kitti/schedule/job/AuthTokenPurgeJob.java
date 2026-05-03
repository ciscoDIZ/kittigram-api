package es.kitti.schedule.job;

import es.kitti.schedule.client.AuthInternalClient;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class AuthTokenPurgeJob {

    @Inject
    @RestClient
    AuthInternalClient client;

    @ConfigProperty(name = "kitties.internal.secret")
    String secret;

    @Scheduled(cron = "0 0 3 ? * SUN")
    Uni<Void> run() {
        Log.info("Triggering expired refresh token purge");
        return client.triggerTokenPurge(secret)
                .invoke(() -> Log.info("Refresh token purge triggered"))
                .replaceWithVoid();
    }
}