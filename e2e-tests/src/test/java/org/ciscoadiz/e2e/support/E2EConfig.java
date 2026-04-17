package org.ciscoadiz.e2e.support;

import io.restassured.RestAssured;
import org.awaitility.Awaitility;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class E2EConfig {

    public static final String GATEWAY_URL = System.getenv().getOrDefault("GATEWAY_URL", "http://localhost:8080");
    public static final String MAILHOG_URL = System.getenv().getOrDefault("MAILHOG_URL", "http://localhost:8025");
    public static final String FRONTEND_URL = System.getenv().getOrDefault("FRONTEND_URL", "http://localhost:5173");

    private static final AtomicBoolean STACK_READY = new AtomicBoolean(false);

    /**
     * Polls the gateway until both the cat-service (public list endpoint) and auth-service
     * (login with bad creds → 401, not 500/connection-refused) respond correctly.
     * Idempotent: subsequent calls return immediately once the stack has been confirmed.
     */
    public static void waitForStack() {
        RestAssured.baseURI = GATEWAY_URL;
        if (STACK_READY.get()) return;

        // Phase 1: wait for cat-service to be reachable (gateway + cat-service + DB)
        Awaitility.await("cat-service ready")
                .atMost(120, TimeUnit.SECONDS)
                .pollInterval(3, TimeUnit.SECONDS)
                .ignoreExceptions()
                .until(() -> {
                    int s = RestAssured.get("/api/cats").statusCode();
                    return s == 200 || s == 204;
                });

        // Phase 2: wait for auth-service to be reachable (auth + user-service gRPC + DB + Kafka)
        // An unknown-email login returns 401 when services are healthy; 500/503 while warming up.
        Awaitility.await("auth-service ready")
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(3, TimeUnit.SECONDS)
                .ignoreExceptions()
                .until(() -> {
                    int s = RestAssured.given()
                            .contentType("application/json")
                            .body("{\"email\":\"probe@e2e.ready\",\"password\":\"probe\"}")
                            .post("/api/auth/login")
                            .statusCode();
                    // 401 = auth-service is up and rejected credentials correctly
                    // 400 = validation layer up (also acceptable)
                    return s == 401 || s == 400;
                });

        STACK_READY.set(true);
    }

    private E2EConfig() {}
}
