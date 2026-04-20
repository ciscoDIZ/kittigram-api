package es.kitti.auth.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens", schema = "auth")
public class RefreshToken extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String token;

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public String email;

    @Column(nullable = false)
    public String role;

    @Column(nullable = false)
    public LocalDateTime expiresAt;

    @Column(nullable = false)
    public boolean revoked = false;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
