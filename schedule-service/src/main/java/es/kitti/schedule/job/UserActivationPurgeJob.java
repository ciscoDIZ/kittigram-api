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
public class UserActivationPurgeJob {

    @Inject
    @RestClient
    UserInternalClient client;

    @ConfigProperty(name = "kitties.internal.secret")
    String secret;

    @Scheduled(cron = "0 15 2 * * ?")
    Uni<Void> run() {
        Log.info("Triggering expired activation token purge");
        return client.triggerActivationPurge(secret)
                .invoke(() -> Log.info("Activation token purge triggered"))
                .replaceWithVoid();
    }
}
