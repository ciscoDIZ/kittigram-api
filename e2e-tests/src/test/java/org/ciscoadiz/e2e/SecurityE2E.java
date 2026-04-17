package org.ciscoadiz.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.ciscoadiz.e2e.support.E2EConfig;
import org.ciscoadiz.e2e.support.MailHogClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.equalTo;

class SecurityE2E {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TS = System.currentTimeMillis();

    private static final String USER_EMAIL = "sec_user_" + TS + "@e2e.test";
    private static final String USER_PASSWORD = "Password1!";
    private static final String ORG_EMAIL = "sec_org_" + TS + "@e2e.test";
    private static final String ORG_PASSWORD = "Password1!";
    private static final String THIRD_EMAIL = "sec_third_" + TS + "@e2e.test";
    private static final String THIRD_PASSWORD = "Password1!";

    private static String userToken;
    private static String orgToken;
    private static String thirdUserToken;
    private static Long adoptionId;

    @BeforeAll
    static void setup() throws Exception {
        E2EConfig.waitForStack();
        MailHogClient.deleteAll();

        // Register and activate User
        given().contentType(ContentType.JSON)
            .body(Map.of("email", USER_EMAIL, "password", USER_PASSWORD,
                "name", "Sec", "surname", "User", "role", "User"))
            .post("/api/users").then().statusCode(201);

        // Register and activate Organization
        given().contentType(ContentType.JSON)
            .body(Map.of("email", ORG_EMAIL, "password", ORG_PASSWORD,
                "name", "Sec", "surname", "Org", "role", "Organization"))
            .post("/api/users").then().statusCode(201);

        // Register and activate third user (for IDOR test)
        given().contentType(ContentType.JSON)
            .body(Map.of("email", THIRD_EMAIL, "password", THIRD_PASSWORD,
                "name", "Third", "surname", "User", "role", "User"))
            .post("/api/users").then().statusCode(201);

        activateUser(USER_EMAIL);
        activateUser(ORG_EMAIL);
        activateUser(THIRD_EMAIL);

        userToken = login(USER_EMAIL, USER_PASSWORD);
        orgToken = login(ORG_EMAIL, ORG_PASSWORD);
        thirdUserToken = login(THIRD_EMAIL, THIRD_PASSWORD);

        // Create a cat and an adoption for IDOR test
        Long orgId = extractSubFromJwt(orgToken);
        Long catId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + orgToken)
            .body(Map.of("name", "Mochi", "age", 1, "sex", "Male",
                "city", "Barcelona", "country", "Spain"))
            .post("/api/cats").then().statusCode(201)
            .extract().jsonPath().getLong("id");

        adoptionId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + userToken)
            .body(Map.of("catId", catId, "organizationId", orgId))
            .post("/api/adoptions").then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    @Test
    void noToken_protectedEndpoint_returns401() {
        given()
        .when()
            .get("/api/users/active")
        .then()
            .statusCode(401);
    }

    @Test
    void invalidBearerToken_returns401() {
        given()
            .header("Authorization", "Bearer this.is.garbage")
        .when()
            .get("/api/adoptions/my")
        .then()
            .statusCode(401);
    }

    @Test
    void userRole_orgEndpoint_returns403() {
        given()
            .header("Authorization", "Bearer " + userToken)
        .when()
            .get("/api/adoptions/organization")
        .then()
            .statusCode(403);
    }

    @Test
    void orgRole_userEndpoint_returns403() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + orgToken)
            .body(Map.of("catId", 1, "organizationId", 1))
        .when()
            .post("/api/adoptions")
        .then()
            .statusCode(403);
    }

    @Test
    void idor_thirdPartyCannotViewAdoption_returns403() {
        given()
            .header("Authorization", "Bearer " + thirdUserToken)
        .when()
            .get("/api/adoptions/" + adoptionId)
        .then()
            .statusCode(403);
    }

    @Test
    void publicCats_noToken_returns200() {
        given()
        .when()
            .get("/api/cats")
        .then()
            .statusCode(200);
    }

    @Test
    void publicCatDetail_noToken_notUnauthorized() {
        int status = given()
        .when()
            .get("/api/cats/99999999")
            .statusCode();
        // Must be 404 Not Found, NOT 401 Unauthorized
        org.junit.jupiter.api.Assertions.assertNotEquals(401, status);
        org.junit.jupiter.api.Assertions.assertEquals(404, status);
    }

    @Test
    void validation_blankLoginBody_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", "", "password", ""))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(400);
    }

    // ---- helpers ----

    private static void activateUser(String email) {
        String body = MailHogClient.waitForEmail(email);
        String token = MailHogClient.extractActivationToken(body);
        given().contentType(ContentType.JSON)
            .body(Map.of("token", token))
            .post("/api/users/activate").then().statusCode(200);
    }

    private static String login(String email, String password) {
        return given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", email, "password", password))
            .post("/api/auth/login").then().statusCode(200)
            .extract().jsonPath().getString("accessToken");
    }

    private static Long extractSubFromJwt(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode node = MAPPER.readTree(payload);
        return node.get("sub").asLong();
    }
}
