package es.kitti.cat.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cat_images", schema = "cats")
public class CatImage extends PanacheEntity {

    @Column(nullable = false)
    public Long catId;

    @Column(nullable = false)
    public String key;

    @Column(nullable = false)
    public String url;

    @Column(name = "image_order", nullable = false)
    public Integer imageOrder;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }
}