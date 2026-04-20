package es.kitti.formanalysis.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import es.kitti.formanalysis.entity.FormAnalysis;
import es.kitti.formanalysis.entity.FormFlag;
import es.kitti.formanalysis.repository.FormAnalysisRepository;
import es.kitti.formanalysis.repository.FormFlagRepository;

import java.util.List;

@ApplicationScoped
public class FormAnalysisPersistenceService {

    @Inject
    FormAnalysisRepository formAnalysisRepository;

    @Inject
    FormFlagRepository formFlagRepository;

    @WithTransaction
    public Uni<FormAnalysis> persist(FormAnalysis analysis, List<FormFlag> flags) {
        return formAnalysisRepository.persist(analysis)
                .onItem().transformToUni(saved -> {
                    if (flags.isEmpty()) {
                        return Uni.createFrom().item(saved);
                    }
                    flags.forEach(f -> f.formAnalysisId = saved.id);
                    return io.smallrye.mutiny.Multi.createFrom().iterable(flags)
                            .onItem().transformToUniAndConcatenate(flag ->
                                    formFlagRepository.persist(flag)
                            )
                            .collect().asList()
                            .onItem().transform(v -> saved);
                });
    }
}