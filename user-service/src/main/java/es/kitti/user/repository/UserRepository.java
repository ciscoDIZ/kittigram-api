package es.kitti.user.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.user.entity.User;
import es.kitti.user.entity.UserStatus;
import es.kitti.user.exception.UserNotFoundException;

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

    @WithSession
    public Uni<User> findByActivationToken(String token) {
        return find("activationToken", token).firstResult();
    }
}
