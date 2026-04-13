package org.ciscoadiz.gateway.filter;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.util.Set;
import java.util.regex.Pattern;

@ApplicationScoped
public class JwtAuthFilter {

    private static final Set<String> PUBLIC_EXACT = Set.of(
            "POST:/api/auth/login",
            "POST:/api/auth/refresh",
            "POST:/api/users"
    );

    private static final Set<Pattern> PUBLIC_PATTERNS = Set.of(
            Pattern.compile("GET:/api/cats"),
            Pattern.compile("GET:/api/cats/\\d+")
    );

    @ServerRequestFilter
    public Uni<Response> filter(ContainerRequestContext ctx) {
        String method = ctx.getMethod();
        String path = ctx.getUriInfo().getPath();
        String key = method + ":" + path;

        if (PUBLIC_EXACT.contains(key)) {
            return Uni.createFrom().nullItem();
        }

        for (Pattern pattern : PUBLIC_PATTERNS) {
            if (pattern.matcher(key).matches()) {
                return Uni.createFrom().nullItem();
            }
        }

        String auth = ctx.getHeaderString("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return Uni.createFrom().item(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"status\":401,\"message\":\"Missing or invalid token\"}")
                            .build()
            );
        }

        // El token se valida mediante quarkus-smallrye-jwt automáticamente
        // Si llega aquí con Bearer, Quarkus verifica la firma y expiración
        return Uni.createFrom().nullItem();
    }
}