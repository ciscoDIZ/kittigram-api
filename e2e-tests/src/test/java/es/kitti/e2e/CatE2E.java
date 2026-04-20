package es.kitti.e2e;

import io.restassured.http.ContentType;
import es.kitti.e2e.support.E2EConfig;
import es.kitti.e2e.support.MailHogClient;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatE2E {

    private static final long TS = System.currentTimeMillis();
    private static final String OWNER_EMAIL = "cat_owner_" + TS + "@e2e.test";
    private static final String OTHER_EMAIL = "cat_other_" + TS + "@e2e.test";
    private static final String PASSWORD = "Password1!";

    private static final byte[] TINY_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xD9
    };

    private static final String TEST_IP = "cat-test-" + TS;

    private static String ownerToken;
    private static String otherToken;
    private static Long catId;
    private static Long imageId;

    @BeforeAll
    static void setup() {
        E2EConfig.waitForStack();

        register(OWNER_EMAIL, "Owner", "Cat");
        register(OTHER_EMAIL, "Other", "User");

        activate(OWNER_EMAIL);
        activate(OTHER_EMAIL);

        ownerToken = login(OWNER_EMAIL, PASSWORD);
        otherToken = login(OTHER_EMAIL, PASSWORD);
    }

    // --- GET /cats (public search) ---

    @Test @Order(1)
    void searchCats_noToken_returns200() {
        given()
        .when()
            .get("/api/cats")
        .then()
            .statusCode(200);
    }

    @Test @Order(2)
    void searchCats_byCity_returns200() {
        given()
            .queryParam("city", "Madrid")
        .when()
            .get("/api/cats")
        .then()
            .statusCode(200);
    }

    // --- POST /cats ---

    @Test @Order(3)
    void createCat_noToken_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "Luna", "age", 2, "sex", "Female",
                    "city", "Madrid", "country", "Spain"))
        .when()
            .post("/api/cats")
        .then()
            .statusCode(401);
    }

    @Test @Order(4)
    void createCat_missingRequiredField_returns400() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + ownerToken)
            .body(Map.of("age", 2, "sex", "Female", "city", "Madrid", "country", "Spain"))
        .when()
            .post("/api/cats")
        .then()
            .statusCode(400);
    }

    @Test @Order(5)
    void createCat_valid_returns201WithFields() {
        var response = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + ownerToken)
            .body(Map.of(
                "name", "Luna",
                "age", 2,
                "sex", "Female",
                "city", "Madrid",
                "country", "Spain",
                "neutered", true
            ))
        .when()
            .post("/api/cats")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Luna"))
            .body("age", equalTo(2))
            .body("sex", equalTo("Female"))
            .body("city", equalTo("Madrid"))
            .body("country", equalTo("Spain"))
            .body("neutered", equalTo(true))
            .body("status", equalTo("Available"))
            .extract().response();

        catId = response.jsonPath().getLong("id");
    }

    // --- GET /cats/{id} (public) ---

    @Test @Order(6)
    void getCat_byId_noToken_returns200() {
        given()
        .when()
            .get("/api/cats/" + catId)
        .then()
            .statusCode(200)
            .body("id", equalTo(catId.intValue()))
            .body("name", equalTo("Luna"));
    }

    @Test @Order(7)
    void getCat_notFound_returns404() {
        given()
        .when()
            .get("/api/cats/999999999")
        .then()
            .statusCode(404);
    }

    // --- GET /cats with city filter matching created cat ---

    @Test @Order(8)
    void searchCats_byCityMadrid_containsCreatedCat() {
        given()
            .queryParam("city", "Madrid")
        .when()
            .get("/api/cats")
        .then()
            .statusCode(200)
            .body("id", hasItem(catId.intValue()));
    }

    // --- PUT /cats/{id} ---

    @Test @Order(9)
    void updateCat_otherUser_returns403() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + otherToken)
            .body(Map.of("name", "Hacker"))
        .when()
            .put("/api/cats/" + catId)
        .then()
            .statusCode(403);
    }

    @Test @Order(10)
    void updateCat_owner_returns200WithUpdatedFields() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + ownerToken)
            .body(Map.of("name", "Luna Updated", "description", "Very cute"))
        .when()
            .put("/api/cats/" + catId)
        .then()
            .statusCode(200)
            .body("name", equalTo("Luna Updated"))
            .body("description", equalTo("Very cute"));
    }

    // --- POST /cats/{id}/images ---

    @Test @Order(11)
    void uploadImage_otherUser_returns403() {
        given()
            .header("Authorization", "Bearer " + otherToken)
            .header("X-Forwarded-For", TEST_IP)
            .multiPart("file", "cat.jpg", TINY_JPEG, "image/jpeg")
        .when()
            .post("/api/cats/" + catId + "/images")
        .then()
            .statusCode(403);
    }

    @Test @Order(12)
    void uploadImage_owner_returns200WithImage() {
        var response = given()
            .header("Authorization", "Bearer " + ownerToken)
            .header("X-Forwarded-For", TEST_IP)
            .multiPart("file", "cat.jpg", TINY_JPEG, "image/jpeg")
        .when()
            .post("/api/cats/" + catId + "/images")
        .then()
            .statusCode(200)
            .body("images", hasSize(greaterThanOrEqualTo(1)))
            .extract().response();

        imageId = response.jsonPath().getLong("images[0].id");
    }

    // --- DELETE /cats/{catId}/images/{imageId} ---

    @Test @Order(13)
    void deleteImage_otherUser_returns403() {
        given()
            .header("Authorization", "Bearer " + otherToken)
        .when()
            .delete("/api/cats/" + catId + "/images/" + imageId)
        .then()
            .statusCode(403);
    }

    @Test @Order(14)
    void deleteImage_owner_returns204() {
        given()
            .header("Authorization", "Bearer " + ownerToken)
        .when()
            .delete("/api/cats/" + catId + "/images/" + imageId)
        .then()
            .statusCode(204);
    }

    // --- DELETE /cats/{id} ---

    @Test @Order(15)
    void deleteCat_otherUser_returns403() {
        given()
            .header("Authorization", "Bearer " + otherToken)
        .when()
            .delete("/api/cats/" + catId)
        .then()
            .statusCode(403);
    }

    @Test @Order(16)
    void deleteCat_owner_returns204() {
        given()
            .header("Authorization", "Bearer " + ownerToken)
        .when()
            .delete("/api/cats/" + catId)
        .then()
            .statusCode(204);
    }

    @Test @Order(17)
    void getCat_afterDelete_returns404() {
        given()
        .when()
            .get("/api/cats/" + catId)
        .then()
            .statusCode(404);
    }

    // --- helpers ---

    private static void register(String email, String name, String surname) {
        given().contentType(ContentType.JSON)
            .body(Map.of("email", email, "password", PASSWORD,
                "name", name, "surname", surname, "role", "User"))
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