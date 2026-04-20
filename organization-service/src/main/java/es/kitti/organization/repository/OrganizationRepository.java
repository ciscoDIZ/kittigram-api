package es.kitti.organization.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.organization.entity.Organization;

@ApplicationScoped
public class OrganizationRepository implements PanacheRepository<Organization> {

    public Uni<Boolean> existsByName(String name) {
        return count("name", name).onItem().transform(c -> c > 0);
    }
}
