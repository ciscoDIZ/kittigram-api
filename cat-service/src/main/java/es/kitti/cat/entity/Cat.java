package es.kitti.cat.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cats", schema = "cats")
public class Cat extends PanacheEntity {

    @Column(nullable = false)
    public String name;

    @Column
    public Integer age;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CatSex sex;

    @Column(length = 1000)
    public String description;

    @Column(nullable = false)
    public Boolean neutered = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CatStatus status;

    @Column
    public String profileImageUrl;

    @Column(name = "organization_id", nullable = false)
    public Long organizationId;

    @Column(nullable = false)
    public String city;

    @Column
    public String region;

    @Column(nullable = false)
    public String country;

    @Column
    public Double latitude;

    @Column
    public Double longitude;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        status = CatStatus.Available;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}