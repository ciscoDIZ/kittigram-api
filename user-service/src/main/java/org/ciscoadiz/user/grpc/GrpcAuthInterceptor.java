package org.ciscoadiz.user.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.quarkus.grpc.GlobalInterceptor;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@GlobalInterceptor
@ApplicationScoped
public class GrpcAuthInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> TOKEN_KEY =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @ConfigProperty(name = "grpc.internal.secret")
    String secret;

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(
            ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {

        String token = headers.get(TOKEN_KEY);
        if (!secret.equals(token)) {
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Missing or invalid internal token"),
                    new Metadata()
            );
            return new ServerCall.Listener<>() {};
        }
        return next.startCall(call, headers);
    }
}