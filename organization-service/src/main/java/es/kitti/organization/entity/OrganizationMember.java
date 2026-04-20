package es.kitti.organization.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "organization_members", schema = "organization",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "user_id"}))
public class OrganizationMember extends PanacheEntity {

    @Column(name = "organization_id", nullable = false)
    public Long organizationId;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public MemberStatus status;

    @Column(nullable = false, updatable = false)
    public LocalDateTime joinedAt;

    @PrePersist
    public void prePersist() {
        joinedAt = LocalDateTime.now();
        if (status == null) status = MemberStatus.Active;
    }
}
