package es.kitti.organization.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "organizations", schema = "organization")
public class Organization extends PanacheEntity {

    @Column(nullable = false)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column
    public String address;

    @Column
    public String city;

    @Column
    public String region;

    @Column
    public String country;

    @Column
    public String phone;

    @Column
    public String email;

    @Column
    public String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OrganizationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OrganizationPlan plan;

    @Column(nullable = false)
    public int maxMembers;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        status = OrganizationStatus.Active;
        if (plan == null) plan = OrganizationPlan.Free;
        maxMembers = plan.maxMembers;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
