package es.kitti.user.entity;


import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", schema = "users")
public class User extends PanacheEntity {
    @Column(nullable = false, unique = true)
    public String email;
    @Column(name = "password_hash", nullable = false)
    public String passwordHash;
    @Column(nullable = false)
    public String name;
    @Column(nullable = false)
    public String surname;
    @Column
    public LocalDate birthdate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public UserStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public UserRole role;
    @Column(name = "activation_token", unique = true)
    public String activationToken;
    @Column(name = "activation_token_expires_at")
    public LocalDateTime activationTokenExpiresAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
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
