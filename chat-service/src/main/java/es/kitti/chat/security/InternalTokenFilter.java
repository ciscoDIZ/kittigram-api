package es.kitti.chat.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Provider
@InternalOnly
public class InternalTokenFilter implements ContainerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    @ConfigProperty(name = "kitties.internal.secret")
    String secret;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String token = ctx.getHeaderString(HEADER);
        if (token == null || !token.equals(secret)) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"status\":401,\"message\":\"Missing or invalid internal token\"}")
                    .build());
        }
    }
}