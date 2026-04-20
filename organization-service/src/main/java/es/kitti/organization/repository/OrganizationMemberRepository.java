package es.kitti.organization.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.organization.entity.MemberRole;
import es.kitti.organization.entity.MemberStatus;
import es.kitti.organization.entity.OrganizationMember;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OrganizationMemberRepository implements PanacheRepository<OrganizationMember> {

    public Uni<List<OrganizationMember>> findActiveByOrganizationId(Long organizationId) {
        return list("organizationId = ?1 and status = ?2", organizationId, MemberStatus.Active);
    }

    @WithSession
    public Uni<Optional<OrganizationMember>> findActiveByOrganizationIdAndUserId(Long organizationId, Long userId) {
        return find("organizationId = ?1 and userId = ?2 and status = ?3", organizationId, userId, MemberStatus.Active)
                .firstResult()
                .onItem().transform(Optional::ofNullable);
    }

    @WithSession
    public Uni<Optional<OrganizationMember>> findActiveByUserId(Long userId) {
        return find("userId = ?1 and status = ?2", userId, MemberStatus.Active)
                .firstResult()
                .onItem().transform(Optional::ofNullable);
    }

    @WithSession
    public Uni<Long> countActiveByOrganizationId(Long organizationId) {
        return count("organizationId = ?1 and status = ?2", organizationId, MemberStatus.Active);
    }

    @WithSession
    public Uni<Boolean> isAdmin(Long organizationId, Long userId) {
        return count("organizationId = ?1 and userId = ?2 and role = ?3 and status = ?4",
                organizationId, userId, MemberRole.Admin, MemberStatus.Active)
                .onItem().transform(c -> c > 0);
    }
}
