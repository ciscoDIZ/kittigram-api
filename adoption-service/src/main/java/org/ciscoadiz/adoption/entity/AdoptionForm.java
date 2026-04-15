package org.ciscoadiz.adoption.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "adoption_forms", schema = "adoption")
public class AdoptionForm extends PanacheEntity {

    @Column(nullable = false)
    public Long adoptionRequestId;

    @Column
    public String housingType;

    @Column
    public Boolean hasGarden;

    @Column
    public Integer familySize;

    @Column
    public Boolean hasOtherPets;

    @Column
    public String otherPetsDescription;

    @Column
    public Boolean hasChildren;

    @Column
    public String experienceWithAnimals;

    @Column
    public String motivations;

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