package es.kitti.formanalysis.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "form_analyses", schema = "form_analysis")
public class FormAnalysis extends PanacheEntity {

    @Column(nullable = false)
    public Long adoptionRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AnalysisDecision decision;

    @Column
    public String rejectionReason;

    @Column(nullable = false)
    public Integer criticalFlags;

    @Column(nullable = false)
    public Integer warningFlags;

    @Column(nullable = false)
    public Integer noticeFlags;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}