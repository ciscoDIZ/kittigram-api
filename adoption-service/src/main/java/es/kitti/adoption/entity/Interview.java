package es.kitti.adoption.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interviews", schema = "adoption")
public class Interview extends PanacheEntity {

    @Column(nullable = false)
    public Long adoptionRequestId;

    @Column(nullable = false)
    public LocalDateTime scheduledAt;

    @Column
    public String notes;

    @Column
    public Boolean passed;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}