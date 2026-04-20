package es.kitti.adoption.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.adoption.entity.Expense;

import java.util.List;

@ApplicationScoped
public class ExpenseRepository implements PanacheRepository<Expense> {

    public Uni<List<Expense>> findByAdoptionRequestId(Long adoptionRequestId) {
        return list("adoptionRequestId", adoptionRequestId);
    }
}