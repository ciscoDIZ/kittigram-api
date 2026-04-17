package org.ciscoadiz.user.grpc;


import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.ciscoadiz.user.repository.UserRepository;

@GrpcService
public class UserGrpcService implements UserService {

    @Inject
    UserRepository userRepository;

    @WithSession
    @Override
    public Uni<ValidateCredentialsResponse> validateCredentials(ValidateCredentialsRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .onItem().transform(user -> {
                    if (user == null || !BcryptUtil.matches(request.getPassword(), user.passwordHash)) {
                        return ValidateCredentialsResponse.newBuilder()
                                .setValid(false)
                                .build();
                    }
                    return ValidateCredentialsResponse.newBuilder()
                            .setValid(true)
                            .setUserId(user.id)
                            .setEmail(user.email)
                            .setStatus(user.status.name())
                            .setRole(user.role.name())
                            .build();
                });
    }

    @WithSession
    @Override
    public Uni<GetUserResponse> getUserById(GetUserByIdRequest request) {
        return userRepository.findById(request.getUserId())
                .onItem().transform(user -> {
                    if (user == null) {
                        return GetUserResponse.newBuilder().build();
                    }
                    return GetUserResponse.newBuilder()
                            .setId(user.id)
                            .setEmail(user.email)
                            .setName(user.name)
                            .setSurname(user.surname)
                            .setStatus(user.status.name())
                            .build();
                });
    }
}
