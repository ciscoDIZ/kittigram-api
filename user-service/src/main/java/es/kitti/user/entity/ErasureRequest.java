package es.kitti.user.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "erasure_requests", schema = "audit")
public class ErasureRequest extends PanacheEntity {

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public LocalDateTime requestedAt;

    @Column(length = 45)
    public String requestedIp;

    @Column(nullable = false)
    public LocalDateTime scheduledPurgeAt;

    @Column
    public LocalDateTime purgedAt;

    @Column(nullable = false)
    public boolean blockedByHold = false;

    @Column
    public LocalDateTime holdLiftedAt;
}
