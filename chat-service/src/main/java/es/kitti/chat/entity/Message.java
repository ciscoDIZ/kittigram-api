package es.kitti.chat.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages", schema = "chat")
public class Message extends PanacheEntity {

    @Column(nullable = false)
    public Long conversationId;

    @Column(nullable = false)
    public Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SenderType senderType;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String content;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}