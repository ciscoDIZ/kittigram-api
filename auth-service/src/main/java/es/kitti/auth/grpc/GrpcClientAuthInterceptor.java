package es.kitti.auth.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.quarkus.grpc.GlobalInterceptor;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@GlobalInterceptor
@ApplicationScoped
public class GrpcClientAuthInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> TOKEN_KEY =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @ConfigProperty(name = "kitties.internal.secret")
    String secret;

    @Override
    public <Q, R> ClientCall<Q, R> interceptCall(
            MethodDescriptor<Q, R> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<R> responseListener, Metadata headers) {
                headers.put(TOKEN_KEY, secret);
                super.start(responseListener, headers);
            }
        };
    }
}