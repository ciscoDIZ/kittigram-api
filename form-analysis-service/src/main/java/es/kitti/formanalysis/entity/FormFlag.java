package es.kitti.formanalysis.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "form_flags", schema = "form_analysis")
public class FormFlag extends PanacheEntity {

    @Column(nullable = false)
    public Long formAnalysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public FlagSeverity severity;

    @Column(nullable = false)
    public String code;

    @Column(nullable = false)
    public String description;
}