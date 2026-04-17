package org.ciscoadiz.auth.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.ciscoadiz.auth.entity.RefreshToken;


@ApplicationScoped
public class RefreshTokenRepository implements PanacheRepository<RefreshToken> {
    @WithTransaction
    public Uni<RefreshToken> findByToken(String token) {
        return find("token", token).firstResult();
    }

    public Uni<Integer> revokeAllByUserId(Long userId) {
        return update("revoked = true where userId = ?1 and revoked = false", userId);
    }
}