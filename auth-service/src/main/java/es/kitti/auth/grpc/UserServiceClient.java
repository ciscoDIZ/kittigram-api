package es.kitti.auth.grpc;

import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.user.grpc.UserService;
import es.kitti.user.grpc.ValidateCredentialsRequest;
import es.kitti.user.grpc.ValidateCredentialsResponse;

@ApplicationScoped
public class UserServiceClient {

    @GrpcClient("user-service")
    UserService userService;

    public Uni<ValidateCredentialsResponse> validateCredentials(String email, String password) {
        return userService.validateCredentials(
                ValidateCredentialsRequest.newBuilder()
                        .setEmail(email)
                        .setPassword(password)
                        .build()
        );
    }
}
