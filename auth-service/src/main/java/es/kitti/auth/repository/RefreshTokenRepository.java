package es.kitti.auth.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.auth.entity.RefreshToken;

import java.time.LocalDateTime;


@ApplicationScoped
public class RefreshTokenRepository implements PanacheRepository<RefreshToken> {
    @WithTransaction
    public Uni<RefreshToken> findByToken(String token) {
        return find("token", token).firstResult();
    }

    public Uni<Integer> revokeAllByUserId(Long userId) {
        return update("revoked = true where userId = ?1 and revoked = false", userId);
    }

    public Uni<Long> deleteAllByUserId(Long userId) {
        return delete("userId", userId);
    }

    public Uni<Long> deleteExpiredOrRevoked() {
        return delete("revoked = true or expiresAt < ?1", LocalDateTime.now());
    }
}