package es.kitti.cat.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.cat.entity.Cat;
import es.kitti.cat.entity.CatStatus;

import java.util.List;

@ApplicationScoped
public class CatRepository implements PanacheRepository<Cat> {

    @WithSession
    public Uni<List<Cat>> findAvailable() {
        return find("status", CatStatus.Available).list();
    }

    @WithSession
    public Uni<List<Cat>> findByCity(String city) {
        return find("status = ?1 and lower(city) like lower(?2)",
                CatStatus.Available, "%" + city + "%").list();
    }

    @WithSession
    public Uni<List<Cat>> findByName(String name) {
        return find("status = ?1 and lower(name) like lower(?2)",
                CatStatus.Available, "%" + name + "%").list();
    }

    @WithSession
    public Uni<List<Cat>> findByCityAndName(String city, String name) {
        return find("status = ?1 and lower(city) like lower(?2) and lower(name) like lower(?3)",
                CatStatus.Available, "%" + city + "%", "%" + name + "%").list();
    }

    @WithSession
    public Uni<List<Cat>> findByOrganizationId(Long organizationId) {
        return find("organizationId", organizationId).list();
    }
}