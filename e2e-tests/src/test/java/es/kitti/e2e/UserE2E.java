package es.kitti.e2e;

import io.restassured.http.ContentType;
import es.kitti.e2e.support.E2EConfig;
import es.kitti.e2e.support.MailHogClient;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserE2E {

    private static final long TS = System.currentTimeMillis();
    private static final String EMAIL = "user_" + TS + "@e2e.test";
    private static final String OTHER_EMAIL = "user_other_" + TS + "@e2e.test";
    private static final String PASSWORD = "Password1!";

    private static String token;
    private static String otherToken;

    @BeforeAll
    static void setup() {
        E2EConfig.waitForStack();

        register(EMAIL, "Alice", "Tester", "User");
        register(OTHER_EMAIL, "Bob", "Tester", "User");

        activate(EMAIL);
        activate(OTHER_EMAIL);

        token = login(EMAIL, PASSWORD);
        otherToken = login(OTHER_EMAIL, PASSWORD);
    }

    // --- GET /users/{email} ---

    @Test @Order(1)
    void getProfile_ownEmail_returns200WithUserData() {
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/users/" + EMAIL)
        .then()
            .statusCode(200)
            .body("email", equalTo(EMAIL))
            .body("name", equalTo("Alice"))
            .body("status", equalTo("Active"));
    }

    @Test @Order(2)
    void getProfile_anotherUser_returns200() {
        // Any authenticated user can read any profile — no requireSelf on GET
        given()
            .header("Authorization", "Bearer " + otherToken)
        .when()
            .get("/api/users/" + EMAIL)
        .then()
            .statusCode(200)
            .body("email", equalTo(EMAIL));
    }

    @Test @Order(3)
    void getProfile_noToken_returns401() {
        given()
        .when()
            .get("/api/users/" + EMAIL)
        .then()
            .statusCode(401);
    }

    // --- GET /users/active ---

    @Test @Order(4)
    void listActiveUsers_authenticated_returns200() {
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/users/active")
        .then()
            .statusCode(200);
    }

    // --- PUT /users/{email} (update profile) ---

    @Test @Order(5)
    void updateProfile_changesNameAndSurname() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + token)
            .body(Map.of("name", "Alicia", "surname", "Updated"))
        .when()
            .put("/api/users/" + EMAIL)
        .then()
            .statusCode(200)
            .body("name", equalTo("Alicia"))
            .body("surname", equalTo("Updated"));
    }

    @Test @Order(6)
    void updateProfile_anotherUser_returns403() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + otherToken)
            .body(Map.of("name", "Hacker"))
        .when()
            .put("/api/users/" + EMAIL)
        .then()
            .statusCode(403);
    }

    // --- PUT /users/{email}/deactivate + /activate ---

    @Test @Order(7)
    void deactivate_anotherUser_returns403() {
        given()
            .header("Authorization", "Bearer " + otherToken)
        .when()
            .put("/api/users/" + EMAIL + "/deactivate")
        .then()
            .statusCode(403);
    }

    @Test @Order(8)
    void deactivate_ownAccount_statusBecomesInactive() {
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .put("/api/users/" + EMAIL + "/deactivate")
        .then()
            .statusCode(200)
            .body("status", equalTo("Inactive"));
    }

    @Test @Order(9)
    void reactivate_ownAccount_statusBecomesActive() {
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .put("/api/users/" + EMAIL + "/activate")
        .then()
            .statusCode(200)
            .body("status", equalTo("Active"));
    }

    // --- Registration validation ---

    @Test @Order(10)
    void register_duplicateEmail_returns409() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD,
                "name", "Dup", "surname", "User"))
        .when()
            .post("/api/users")
        .then()
            .statusCode(409);
    }

    @Test @Order(11)
    void register_blankPassword_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", "new_" + TS + "@e2e.test",
                "password", "", "name", "Test", "surname", "User"))
        .when()
            .post("/api/users")
        .then()
            .statusCode(400);
    }

    @Test @Order(12)
    void register_invalidEmailFormat_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", "not-an-email",
                "password", PASSWORD, "name", "Test", "surname", "User"))
        .when()
            .post("/api/users")
        .then()
            .statusCode(400);
    }

    // --- helpers ---

    private static void register(String email, String name, String surname, String role) {
        given().contentType(ContentType.JSON)
            .body(Map.of("email", email, "password", PASSWORD,
                "name", name, "surname", surname, "role", role))
            .post("/api/users").then().statusCode(201);
    }

    private static void activate(String email) {
        String body = MailHogClient.waitForEmail(email);
        String activationToken = MailHogClient.extractActivationToken(body);
        given().contentType(ContentType.JSON)
            .body(Map.of("token", activationToken))
            .post("/api/users/activate").then().statusCode(200);
    }

    private static String login(String email, String password) {
        return given().contentType(ContentType.JSON)
            .body(Map.of("email", email, "password", password))
            .post("/api/auth/login").then().statusCode(200)
            .extract().jsonPath().getString("accessToken");
    }
}