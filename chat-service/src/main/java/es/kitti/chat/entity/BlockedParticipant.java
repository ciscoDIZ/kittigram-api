package es.kitti.chat.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "blocked_participants", schema = "chat",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_blocked_org_user",
                columnNames = {"organization_id", "user_id"}))
public class BlockedParticipant extends PanacheEntity {

    @Column(name = "organization_id", nullable = false)
    public Long organizationId;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(columnDefinition = "TEXT")
    public String reason;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
