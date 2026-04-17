package org.ciscoadiz.gateway.resource;

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
                    "email": "test@kittigram.org",
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
}