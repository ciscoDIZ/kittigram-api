package es.kitti.gateway.filter;

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
            "POST:/api/auth/logout",
            "POST:/api/users",
            "POST:/api/users/activate",
            "GET:/doc",
            "GET:/swagger-ui"
    );

    private static final Set<Pattern> PUBLIC_PATTERNS = Set.of(
            Pattern.compile("GET:/api/cats"),
            Pattern.compile("GET:/api/cats/\\d+"),
            Pattern.compile("GET:/api/storage/files/.*"),
            Pattern.compile("GET:/api/storage/files/.+"),
            Pattern.compile("GET:/api/openapi/.*"),
            Pattern.compile("GET:/swagger-ui/.*")
    );

    private static final Pattern INTERNAL_PATH = Pattern.compile("^/api/[^/]+/internal(/.*)?$");

    @ServerRequestFilter
    public Uni<Response> filter(ContainerRequestContext ctx) {
        String method = ctx.getMethod();
        String path = ctx.getUriInfo().getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String auth = ctx.getHeaderString("Authorization");

        Log.infof("Gateway request: %s %s - Auth: %s", method, path, auth != null ? "present" : "missing");

        if (INTERNAL_PATH.matcher(path).matches()) {
            return Uni.createFrom().item(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity("{\"status\":404,\"message\":\"Not found\"}")
                            .build()
            );
        }

        String key = method + ":" + path;

        if (PUBLIC_EXACT.contains(key)) {
            return Uni.createFrom().nullItem();
        }

        for (Pattern pattern : PUBLIC_PATTERNS) {
            if (pattern.matcher(key).matches()) {
                Log.infof("Request allowed: %s %s", method, path);
                return Uni.createFrom().nullItem();
            }
        }

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