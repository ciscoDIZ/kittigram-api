package es.kitti.organization.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class OrganizationInternalResourceTest {

    private static final String INTERNAL_TOKEN = "test-internal-secret";

    @Test
    void byRegion_missingToken_returns401() {
        given()
                .when()
                .get("/organizations/internal/by-region/Madrid")
                .then()
                .statusCode(401)
                .body("message", containsString("internal token"));
    }

    @Test
    void byRegion_wrongToken_returns401() {
        given()
                .header("X-Internal-Token", "nope")
                .when()
                .get("/organizations/internal/by-region/Madrid")
                .then()
                .statusCode(401);
    }

    @Test
    void byRegion_validToken_emptyList() {
        given()
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .when()
                .get("/organizations/internal/by-region/RegionWithNoOrgs-" + System.nanoTime())
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "100", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "100")})
    void byRegion_validToken_returnsMatchingOrgs() {
        String region = "TestRegion-" + System.nanoTime();

        given()
                .contentType(ContentType.JSON)
                .body(String.format("""
                        {
                            "name": "Protectora %s",
                            "city": "Madrid",
                            "region": "%s",
                            "phone": "+34900000000",
                            "email": "p@kitti.es"
                        }
                        """, region, region))
                .when()
                .post("/organizations")
                .then()
                .statusCode(201);

        given()
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .when()
                .get("/organizations/internal/by-region/" + region)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].region", equalTo(region))
                .body("[0].city", equalTo("Madrid"))
                .body("[0].phone", equalTo("+34900000000"))
                .body("[0].email", equalTo("p@kitti.es"));
    }
}
