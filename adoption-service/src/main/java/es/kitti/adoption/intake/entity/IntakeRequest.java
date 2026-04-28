package es.kitti.adoption.intake.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "intake_requests", schema = "adoption")
public class IntakeRequest extends PanacheEntity {

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public Long targetOrganizationId;

    @Column(nullable = false)
    public String catName;

    @Column(nullable = false)
    public Integer catAge;

    @Column(nullable = false)
    public String region;

    @Column(nullable = false)
    public String city;

    @Column(nullable = false)
    public Boolean vaccinated;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IntakeStatus status;

    @Column
    public String rejectionReason;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column
    public LocalDateTime decidedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (status == null) status = IntakeStatus.Pending;
    }
}
