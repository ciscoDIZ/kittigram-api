package org.ciscoadiz.gateway.ratelimit;

import io.smallrye.faulttolerance.api.RateLimit;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.ciscoadiz.gateway.proxy.ProxyService;

import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class RateLimitedProxy {

    @Inject
    ProxyService proxyService;

    @RateLimit(value = 10, window = 1, windowUnit = ChronoUnit.MINUTES)
    public Uni<Response> proxyLogin(byte[] body, String authHeader, String contentType) {
        return proxyService.proxy("POST", "/api/auth/login", body, authHeader, contentType);
    }

    @RateLimit(value = 20, window = 1, windowUnit = ChronoUnit.MINUTES)
    public Uni<Response> proxyRefresh(byte[] body, String authHeader, String contentType) {
        return proxyService.proxy("POST", "/api/auth/refresh", body, authHeader, contentType);
    }

    @RateLimit(value = 5, window = 1, windowUnit = ChronoUnit.MINUTES)
    public Uni<Response> proxyStorageUpload(byte[] body, String authHeader, String contentType) {
        return proxyService.proxy("POST", "/api/storage/upload", body, authHeader, contentType);
    }
}
