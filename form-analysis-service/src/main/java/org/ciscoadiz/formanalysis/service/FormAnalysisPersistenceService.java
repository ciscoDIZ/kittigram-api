package org.ciscoadiz.formanalysis.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.ciscoadiz.formanalysis.entity.FormAnalysis;
import org.ciscoadiz.formanalysis.entity.FormFlag;
import org.ciscoadiz.formanalysis.repository.FormAnalysisRepository;
import org.ciscoadiz.formanalysis.repository.FormFlagRepository;

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