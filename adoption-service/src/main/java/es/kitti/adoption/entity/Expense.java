package es.kitti.adoption.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import es.kitti.adoption.entity.ExpenseRecipient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses", schema = "adoption")
public class Expense extends PanacheEntity {

    @Column(nullable = false)
    public Long adoptionRequestId;

    @Column(nullable = false)
    public String concept;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ExpenseRecipient recipient;

    @Column(nullable = false)
    public Boolean paid;

    @Column
    public LocalDateTime paidAt;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        paid = false;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}