package org.ciscoadiz.user.entity;


import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", schema = "users")
public class User extends PanacheEntity {
    @Column(nullable = false, unique = true)
    public String email;
    @Column(nullable = false)
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
    @Column(nullable = false, updatable = false )
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
