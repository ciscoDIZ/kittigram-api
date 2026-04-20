package es.kitti.adoption.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "adoption_forms", schema = "adoption")
public class AdoptionForm extends PanacheEntity {

    @Column(nullable = false)
    public Long adoptionRequestId;

    @Column(nullable = false)
    public String fullName;

    @Column(nullable = false)
    public String idNumber;

    @Column(nullable = false)
    public String phone;

    @Column(nullable = false)
    public String address;

    @Column(nullable = false)
    public String city;

    @Column(nullable = false)
    public String postalCode;

    @Column(nullable = false)
    public Boolean acceptsVetVisits;

    @Column(nullable = false)
    public Boolean acceptsFollowUpContact;

    @Column(nullable = false)
    public Boolean acceptsReturnIfNeeded;

    @Column(nullable = false)
    public Boolean acceptsTermsAndConditions;

    @Column(columnDefinition = "TEXT")
    public String additionalNotes;

    @Column(nullable = false)
    public Boolean signedAdoptionContract;

    @Column
    public LocalDateTime contractSignedAt;

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