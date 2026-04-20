package es.kitti.adoption.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "adoption_request_forms", schema = "adoption")
public class AdoptionRequestForm extends PanacheEntity {

    @Column(nullable = false)
    public Long adoptionRequestId;

    @Column(nullable = false)
    public Boolean hasPreviousCatExperience;

    @Column(columnDefinition = "TEXT")
    public String previousPetsHistory;

    @Column(nullable = false)
    public Integer adultsInHousehold;

    @Column(nullable = false)
    public Boolean hasChildren;

    @Column
    public String childrenAges;

    @Column(nullable = false)
    public Boolean hasOtherPets;

    @Column(columnDefinition = "TEXT")
    public String otherPetsDescription;

    @Column(nullable = false)
    public Integer hoursAlonePerDay;

    @Column(nullable = false)
    public Boolean stableHousing;

    @Column(columnDefinition = "TEXT")
    public String housingInstabilityReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public HousingType housingType;

    @Column(nullable = false)
    public Integer housingSize;

    @Column(nullable = false)
    public Boolean hasOutdoorAccess;

    @Column(nullable = false)
    public Boolean isRental;

    @Column
    public Boolean rentalPetsAllowed;

    @Column(nullable = false)
    public Boolean hasWindowsWithView;

    @Column(nullable = false)
    public Boolean hasVerticalSpace;

    @Column(nullable = false)
    public Boolean hasHidingSpots;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ActivityLevel householdActivityLevel;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String whyCatsNeedToPlay;

    @Column(nullable = false)
    public Integer dailyPlayMinutes;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String plannedEnrichment;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String reactionToUnwantedBehavior;

    @Column(nullable = false)
    public Boolean hasScratchingPost;

    @Column(nullable = false)
    public Boolean willingToEnrichEnvironment;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String motivationToAdopt;

    @Column(nullable = false)
    public Boolean understandsLongTermCommitment;

    @Column(nullable = false)
    public Boolean hasVetBudget;

    @Column(nullable = false)
    public Boolean allHouseholdMembersAgree;

    @Column(nullable = false)
    public Boolean anyoneHasAllergies;

    @Column(columnDefinition = "TEXT")
    public String allergiesDetail;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}