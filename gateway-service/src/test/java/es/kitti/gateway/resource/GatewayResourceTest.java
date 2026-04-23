package es.kitti.gateway.resource;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@ConnectWireMock
class GatewayResourceTest {

    private static final long   EPOCH_MS        = System.currentTimeMillis();
    private static final String RL_EMAIL        = "rl-" + EPOCH_MS + "@test.com";
    // Each rate-limited test uses its own unique IP to avoid sharing the same window
    private static final String RL_UPLOAD_IP    = "10.100.1." + (EPOCH_MS % 254 + 1);
    private static final String RL_REFRESH_IP   = "10.104.1." + (EPOCH_MS % 254 + 1);
    private static final String IP_REFRESH      = "10.101.1." + (EPOCH_MS % 254 + 1);
    private static final String IP_UPLOAD       = "10.102.1." + (EPOCH_MS % 254 + 1);
    private static final String IP_FALLBACK     = "10.103.1." + (EPOCH_MS % 254 + 1);

    WireMock wiremock;

    @BeforeEach
    void setUp() {
        wiremock.resetToDefaultMappings();
    }

    @Test
    void testLoginRoutedToAuthService() {
        wiremock.register(post(urlEqualTo("/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "accessToken": "test-token",
                                "refreshToken": "test-refresh"
                            }
                            """)));

        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "email": "test@kitti.es",
                    "password": "password123"
                }
                """)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", equalTo("test-token"));
    }

    @Test
    void testProtectedEndpointWithoutToken() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .post("/api/cats")
                .then()
                .statusCode(401);
    }

    @Test
    void testPublicCatsEndpointRouted() {
        wiremock.register(get(urlEqualTo("/cats"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        given()
                .when()
                .get("/api/cats")
                .then()
                .statusCode(200);
    }

    @Test
    void testUnknownRouteRequiresAuth() {
        given()
                .when()
                .get("/api/unknown")
                .then()
                .statusCode(401);
    }

    @Test
    void testPatchWithoutTokenReturnsUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"Accepted\"}")
                .when()
                .patch("/api/adoptions/1/status")
                .then()
                .statusCode(401);
    }

    @Test
    void testAdoptionsPathStrippedCorrectly() {
        wiremock.register(get(urlEqualTo("/adoptions/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1}")));

        given()
                .header("Authorization", "Bearer test-token")
                .when()
                .get("/api/adoptions/1")
                .then()
                .statusCode(200);
    }

    @Test
    void testRefreshRouted() {
        wiremock.register(post(urlEqualTo("/auth/refresh"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"new-token\"}")));

        given()
                .header("X-Forwarded-For", IP_REFRESH)
                .contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"old-token\"}")
                .when()
                .post("/api/auth/refresh")
                .then()
                .statusCode(200);
    }

    @Test
    void testLogoutRouted() {
        wiremock.register(post(urlEqualTo("/auth/logout"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        given()
                .header("Authorization", "Bearer test-token")
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/auth/logout")
                .then()
                .statusCode(200);
    }

    @Test
    void testGenericPostRouted() {
        wiremock.register(post(urlEqualTo("/users"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1}")));

        given()
                .header("Authorization", "Bearer test-token")
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Test\"}")
                .when()
                .post("/api/users")
                .then()
                .statusCode(201);
    }

    @Test
    void testGenericPutRouted() {
        wiremock.register(put(urlEqualTo("/users/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1}")));

        given()
                .header("Authorization", "Bearer test-token")
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Updated\"}")
                .when()
                .put("/api/users/1")
                .then()
                .statusCode(200);
    }

    @Test
    void testGenericDeleteReturnsNoBody() {
        wiremock.register(delete(urlEqualTo("/cats/1"))
                .willReturn(aResponse()
                        .withStatus(204)));

        given()
                .header("Authorization", "Bearer test-token")
                .when()
                .delete("/api/cats/1")
                .then()
                .statusCode(204);
    }

    @Test
    void testStorageUploadRouted() {
        wiremock.register(post(urlEqualTo("/storage/upload"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"url\":\"file.jpg\"}")));

        given()
                .header("Authorization", "Bearer test-token")
                .header("X-Forwarded-For", IP_UPLOAD)
                .contentType(ContentType.BINARY)
                .body(new byte[]{1, 2, 3})
                .when()
                .post("/api/storage/upload")
                .then()
                .statusCode(200);
    }

    @Test
    void testLoginRateLimitExceeded() {
        wiremock.register(post(urlEqualTo("/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"tok\"}")));

        String body = "{\"email\":\"" + RL_EMAIL + "\",\"password\":\"pass\"}";
        for (int i = 0; i < 2; i++) {
            given().contentType(ContentType.JSON).body(body)
                    .when().post("/api/auth/login")
                    .then().statusCode(200);
        }
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/auth/login")
                .then().statusCode(429)
                .body(containsString("Too many requests"));
    }

    @Test
    void testStorageUploadRateLimitExceeded() {
        wiremock.register(post(urlEqualTo("/storage/upload"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"url\":\"f.jpg\"}")));

        for (int i = 0; i < 2; i++) {
            given().header("Authorization", "Bearer test-token")
                    .header("X-Forwarded-For", RL_UPLOAD_IP)
                    .contentType(ContentType.BINARY).body(new byte[]{1})
                    .when().post("/api/storage/upload")
                    .then().statusCode(200);
        }
        given().header("Authorization", "Bearer test-token")
                .header("X-Forwarded-For", RL_UPLOAD_IP)
                .contentType(ContentType.BINARY).body(new byte[]{1})
                .when().post("/api/storage/upload")
                .then().statusCode(429);
    }

    @Test
    void testLoginWithXForwardedForHeader() {
        wiremock.register(post(urlEqualTo("/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"tok\"}")));

        given()
                .header("X-Forwarded-For", "1.2.3.4")
                .contentType(ContentType.JSON)
                .body("{\"password\":\"pass\"}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200);
    }

    @Test
    void testLoginWithInvalidJsonBodyFallsBackToIp() {
        wiremock.register(post(urlEqualTo("/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"tok\"}")));

        given()
                .header("X-Forwarded-For", IP_FALLBACK)
                .contentType(ContentType.TEXT)
                .body("not-valid-json")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200);
    }

    @Test
    void testUnknownServiceReturns404() {
        given()
                .header("Authorization", "Bearer test-token")
                .when()
                .get("/api/unknown/path")
                .then()
                .statusCode(404);
    }

    @Test
    void testGenericPatchRouted() {
        wiremock.register(patch(urlEqualTo("/adoptions/1/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"Accepted\"}")));

        given()
                .header("Authorization", "Bearer test-token")
                .contentType(ContentType.JSON)
                .body("{\"status\":\"Accepted\"}")
                .when()
                .patch("/api/adoptions/1/status")
                .then()
                .statusCode(200);
    }

    @Test
    void testResponseContainsNoSniffHeader() {
        wiremock.register(get(urlEqualTo("/cats"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        given()
                .when()
                .get("/api/cats")
                .then()
                .statusCode(200)
                .header("X-Content-Type-Options", equalTo("nosniff"));
    }

    @Test
    void testRefreshFallsToRoutingContextIp() {
        wiremock.register(post(urlEqualTo("/auth/refresh"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"tok\"}")));

        // No X-Forwarded-For → clientIp() falls through to RoutingContext.remoteAddress()
        given()
                .contentType(ContentType.JSON)
                .body("{\"refreshToken\":\"old\"}")
                .when()
                .post("/api/auth/refresh")
                .then()
                .statusCode(200);
    }

    @Test
    void testLoginBlankXForwardedForFallsToRoutingContext() {
        wiremock.register(post(urlEqualTo("/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"tok\"}")));

        // blank X-Forwarded-For → forwarded != null but isBlank() → falls to RoutingContext
        given()
                .header("X-Forwarded-For", "   ")
                .contentType(ContentType.JSON)
                .body("{\"password\":\"pass\"}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200);
    }

    @Test
    void testMalformedAuthHeaderReturnsUnauthorized() {
        given()
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .when()
                .get("/api/adoptions/99")
                .then()
                .statusCode(401);
    }

    @Test
    void testRefreshRateLimitExceeded() {
        wiremock.register(post(urlEqualTo("/auth/refresh"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"tok\"}")));

        String body = "{\"refreshToken\":\"old\"}";
        for (int i = 0; i < 2; i++) {
            given().header("X-Forwarded-For", RL_REFRESH_IP)
                    .contentType(ContentType.JSON).body(body)
                    .when().post("/api/auth/refresh")
                    .then().statusCode(200);
        }
        given().header("X-Forwarded-For", RL_REFRESH_IP)
                .contentType(ContentType.JSON).body(body)
                .when().post("/api/auth/refresh")
                .then().statusCode(429);
    }
}