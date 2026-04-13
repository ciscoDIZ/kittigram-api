package org.ciscoadiz.user.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.ciscoadiz.user.entity.User;
import org.ciscoadiz.user.entity.UserStatus;
import org.ciscoadiz.user.exception.UserNotFoundException;

import java.util.List;

@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {
    public Uni<User> findByEmail(String email) {
        return find("email", email)
                .firstResult();
    }

    public Uni<Boolean> existsByEmail(String email) {
        return find("email", email)
                .count()
                .map(count -> count > 0);
    }

    public Uni<Boolean> isActiveAccount(String email) {
        return find("email", email)
                .firstResult()
                .map(user -> user != null && user.status == UserStatus.Active);
    }

    public Multi<User> findAllActiveUsers() {
        return list("status", UserStatus.Active)
                .onItem().transformToMulti(users -> Multi.createFrom().iterable(users));
    }
}
