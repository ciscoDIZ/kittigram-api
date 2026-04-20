package es.kitti.auth.mock;

import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import es.kitti.user.grpc.ValidateCredentialsResponse;


@Alternative
@Priority(1)
@ApplicationScoped
public class MockUserServiceClient {

    public Uni<ValidateCredentialsResponse> validateCredentials(String email, String password) {
        if ("test@kitti.es".equals(email) && "password123".equals(password)) {
            return Uni.createFrom().item(
                    ValidateCredentialsResponse.newBuilder()
                            .setValid(true)
                            .setUserId(1L)
                            .setEmail(email)
                            .build()
            );
        }
        return Uni.createFrom().item(
                ValidateCredentialsResponse.newBuilder()
                        .setValid(false)
                        .build()
        );
    }
}