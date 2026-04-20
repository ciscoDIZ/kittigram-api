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
class OrganizationResourceTest {

    @Test
    void testCreateOrganizationUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Protectora\"}")
                .when()
                .post("/organizations")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "1", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "1")})
    void testCreateOrganizationForbiddenForUserRole() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Protectora\"}")
                .when()
                .post("/organizations")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "1")})
    void testCreateOrganization() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Protectora Test\"}")
                .when()
                .post("/organizations")
                .then()
                .statusCode(201)
                .body("name", equalTo("Protectora Test"))
                .body("plan", equalTo("Free"))
                .body("status", equalTo("Active"))
                .body("maxMembers", equalTo(1));
    }

    @Test
    @TestSecurity(user = "2", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "2")})
    void testCreateOrganizationAndGetMine() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Mi Protectora\"}")
                .when()
                .post("/organizations")
                .then()
                .statusCode(201);

        given()
                .when()
                .get("/organizations/mine")
                .then()
                .statusCode(200)
                .body("name", equalTo("Mi Protectora"));
    }

    @Test
    @TestSecurity(user = "3", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "3")})
    void testGetMineWhenNotMemberReturns404() {
        given()
                .when()
                .get("/organizations/mine")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "4", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "4")})
    void testMemberLimitExceededOnFreePlan() {
        // Create org (user 4 becomes the only ADMIN member, maxMembers=1)
        int orgId = given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Free Protectora\"}")
                .when()
                .post("/organizations")
                .then()
                .statusCode(201)
                .extract().path("id");

        // Invite another member → should fail with 409
        given()
                .contentType(ContentType.JSON)
                .body("{\"userId\":99,\"role\":\"Staff\"}")
                .when()
                .post("/organizations/" + orgId + "/members")
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "5", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "5")})
    void testUpdateOrganization() {
        int orgId = given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Original\"}")
                .when()
                .post("/organizations")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Updated\",\"city\":\"Madrid\"}")
                .when()
                .put("/organizations/" + orgId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Updated"))
                .body("city", equalTo("Madrid"));
    }

    @Test
    @TestSecurity(user = "6", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "6")})
    void testGetOrganizationById() {
        int orgId = given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Visible Org\"}")
                .when()
                .post("/organizations")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when()
                .get("/organizations/" + orgId)
                .then()
                .statusCode(200)
                .body("id", equalTo(orgId));
    }

    @Test
    @TestSecurity(user = "999", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "999")})
    void testNonMemberCannotUpdateOrganization() {
        // user 999 is NOT a member of org 1 (seeded in init-test.sql, admin=user 100)
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Hacked\"}")
                .when()
                .put("/organizations/1")
                .then()
                .statusCode(403);
    }

    @Test
    void testUpdateOrganizationUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Hacked\"}")
                .when()
                .put("/organizations/1")
                .then()
                .statusCode(401);
    }

    @Test
    void testGetOrganizationByIdNotFound() {
        given()
                .header("Authorization", "Bearer fake")
                .when()
                .get("/organizations/99999")
                .then()
                .statusCode(401);
    }
}
