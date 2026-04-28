package es.kitti.organization.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.organization.entity.Organization;
import es.kitti.organization.entity.OrganizationStatus;

import java.util.List;

@ApplicationScoped
public class OrganizationRepository implements PanacheRepository<Organization> {

    public Uni<Boolean> existsByName(String name) {
        return count("name", name).onItem().transform(c -> c > 0);
    }

    public Uni<List<Organization>> findActiveByRegion(String region) {
        return list("region = ?1 and status = ?2 order by name asc",
                region, OrganizationStatus.Active);
    }
}
