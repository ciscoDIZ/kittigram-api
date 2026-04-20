package es.kitti.adoption.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "adoption_requests", schema = "adoption")
public class AdoptionRequest extends PanacheEntity {

    @Column(nullable = false)
    public Long catId;

    @Column(nullable = false)
    public String adopterEmail;

    @Column(nullable = false)
    public Long adopterId;

    @Column(nullable = false)
    public Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AdoptionStatus status;

    @Column
    public String notes;

    @Column
    public String rejectionReason;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        status = AdoptionStatus.Pending;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}