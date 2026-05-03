package es.kitti.adoption.service;

import es.kitti.adoption.repository.AdoptionFormRepository;
import es.kitti.adoption.repository.AdoptionRequestFormRepository;
import es.kitti.adoption.repository.AdoptionRequestRepository;
import es.kitti.adoption.security.IdNumberEncryptionService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class RetentionPurgeService {

    @Inject
    AdoptionRequestRepository adoptionRequestRepository;

    @Inject
    AdoptionFormRepository adoptionFormRepository;

    @Inject
    AdoptionRequestFormRepository adoptionRequestFormRepository;

    @Inject
    IdNumberEncryptionService encryptionService;

    public Uni<Void> purgeRejected() {
        LocalDateTime cutoff = LocalDateTime.now().minusYears(1);
        return Panache.withTransaction(() ->
                adoptionRequestRepository.findRejectedBefore(cutoff)
                        .onItem().transformToUni(requests -> {
                            List<Long> ids = requests.stream().map(r -> r.id).toList();
                            if (ids.isEmpty()) return Uni.createFrom().voidItem();
                            Log.infof("Purging %d rejected adoption requests older than 1 year", ids.size());
                            return adoptionFormRepository.deleteByRequestIds(ids)
                                    .chain(() -> adoptionRequestFormRepository.deleteByRequestIds(ids))
                                    .chain(() -> adoptionRequestRepository.deleteByIds(ids))
                                    .replaceWithVoid();
                        })
        );
    }

    public Uni<Void> anonymizeCompleted() {
        LocalDateTime cutoff = LocalDateTime.now().minusYears(5);
        String placeholder = encryptionService.encrypt("SUPRIMIDO");
        return Panache.withTransaction(() ->
                adoptionRequestRepository.findCompletedBefore(cutoff)
                        .onItem().transformToUni(requests -> {
                            List<Long> ids = requests.stream().map(r -> r.id).toList();
                            if (ids.isEmpty()) return Uni.createFrom().voidItem();
                            Log.infof("Anonymizing PII for %d completed adoption requests older than 5 years", ids.size());
                            return adoptionFormRepository.anonymizeForRequestIds(ids, placeholder)
                                    .chain(() -> adoptionRequestFormRepository.clearAllergiesForRequestIds(ids))
                                    .replaceWithVoid();
                        })
        );
    }
}
