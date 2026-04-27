package es.kitti.cat.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class InternalPingResourceTest {

    @Test
    void missingHeader_returns401() {
        given()
                .when()
                .get("/cats/internal/ping")
                .then()
                .statusCode(401);
    }

    @Test
    void wrongSecret_returns401() {
        given()
                .header("X-Internal-Token", "not-the-secret")
                .when()
                .get("/cats/internal/ping")
                .then()
                .statusCode(401);
    }

    @Test
    void correctSecret_returns200() {
        given()
                .header("X-Internal-Token", "test-internal-secret")
                .when()
                .get("/cats/internal/ping")
                .then()
                .statusCode(200);
    }
}
