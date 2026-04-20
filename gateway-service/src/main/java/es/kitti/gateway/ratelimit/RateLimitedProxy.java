package es.kitti.gateway.ratelimit;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import es.kitti.gateway.proxy.ProxyService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class RateLimitedProxy {

    private static final long MINUTE_MS = 60_000L;

    @Inject
    ProxyService proxyService;

    @Inject
    IpRateLimiter rateLimiter;

    @ConfigProperty(name = "rate-limit.auth.login", defaultValue = "10")
    int loginLimit;

    @ConfigProperty(name = "rate-limit.auth.refresh", defaultValue = "20")
    int refreshLimit;

    @ConfigProperty(name = "rate-limit.storage.upload", defaultValue = "5")
    int uploadLimit;

    public Uni<Response> proxyLogin(String ip, byte[] body, String authHeader, String contentType) {
        if (!rateLimiter.tryAcquire(ip, loginLimit, MINUTE_MS)) {
            throw new RateLimitExceededException();
        }
        return proxyService.proxy("POST", "/api/auth/login", body, authHeader, contentType);
    }

    public Uni<Response> proxyRefresh(String ip, byte[] body, String authHeader, String contentType) {
        if (!rateLimiter.tryAcquire(ip, refreshLimit, MINUTE_MS)) {
            throw new RateLimitExceededException();
        }
        return proxyService.proxy("POST", "/api/auth/refresh", body, authHeader, contentType);
    }

    public Uni<Response> proxyStorageUpload(String ip, byte[] body, String authHeader, String contentType) {
        if (!rateLimiter.tryAcquire(ip, uploadLimit, MINUTE_MS)) {
            throw new RateLimitExceededException();
        }
        return proxyService.proxy("POST", "/api/storage/upload", body, authHeader, contentType);
    }
}
