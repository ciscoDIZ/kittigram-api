package es.kitti.user.scheduler;

import es.kitti.user.service.ErasureService;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ErasurePurgeJob {

    @Inject
    ErasureService erasureService;

    @Scheduled(cron = "0 0 2 * * ?")
    Uni<Void> runPurge() {
        Log.info("Starting nightly erasure purge job");
        return erasureService.purgeEligibleUsers()
                .invoke(() -> Log.info("Erasure purge job completed"));
    }
}