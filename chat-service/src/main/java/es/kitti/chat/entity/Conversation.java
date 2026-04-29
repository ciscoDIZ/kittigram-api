package es.kitti.chat.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations", schema = "chat")
public class Conversation extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public Long intakeRequestId;

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public Long organizationId;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column
    public LocalDateTime lastMessageAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}