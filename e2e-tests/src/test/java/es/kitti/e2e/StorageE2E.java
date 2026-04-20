package es.kitti.e2e;

import io.restassured.http.ContentType;
import es.kitti.e2e.support.E2EConfig;
import es.kitti.e2e.support.MailHogClient;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StorageE2E {

    private static final long TS = System.currentTimeMillis();
    private static final String EMAIL = "storage_" + TS + "@e2e.test";
    private static final String PASSWORD = "Password1!";

    // Minimal valid JPEG (SOI + EOI markers)
    private static final byte[] TINY_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xD9
    };

    // Unique per-run IP prevents the rate-limit bucket filled by the last test
    // from bleeding into the next test run within the 60-second window.
    private static final String TEST_IP = "test-" + TS;

    private static String token;
    private static String uploadedKey;
    private static String uploadedUrl;

    @BeforeAll
    static void setup() {
        E2EConfig.waitForStack();

        given().contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD,
                "name", "Storage", "surname", "Tester", "role", "User"))
            .post("/api/users").then().statusCode(201);

        String emailBody = MailHogClient.waitForEmail(EMAIL);
        String activationToken = MailHogClient.extractActivationToken(emailBody);
        given().contentType(ContentType.JSON)
            .body(Map.of("token", activationToken))
            .post("/api/users/activate").then().statusCode(200);

        token = given().contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD))
            .post("/api/auth/login").then().statusCode(200)
            .extract().jsonPath().getString("accessToken");
    }

    @Test @Order(1)
    void upload_validJpeg_returns200WithKeyAndUrl() {
        var response = given()
            .header("Authorization", "Bearer " + token)
            .header("X-Forwarded-For", TEST_IP)
            .multiPart("file", "test.jpg", TINY_JPEG, "image/jpeg")
        .when()
            .post("/api/storage/upload")
        .then()
            .statusCode(200)
            .body("key", notNullValue())
            .body("url", notNullValue())
            .extract().response();

        uploadedKey = response.jsonPath().getString("key");
        uploadedUrl = response.jsonPath().getString("url");
        assertNotNull(uploadedKey);
    }

    @Test @Order(2)
    void serveFile_noToken_returns200() {
        assertNotNull(uploadedKey, "depends on upload test");
        // URL format: http://localhost:8080/api/storage/files/{key}
        String path = "/api/storage/files/" + uploadedKey;
        given()
        .when()
            .get(path)
        .then()
            .statusCode(200);
    }

    @Test @Order(3)
    void upload_noToken_returns401() {
        given()
            .multiPart("file", "test.jpg", TINY_JPEG, "image/jpeg")
        .when()
            .post("/api/storage/upload")
        .then()
            .statusCode(401);
    }

    @Test @Order(4)
    void upload_invalidType_returns400() {
        given()
            .header("Authorization", "Bearer " + token)
            .header("X-Forwarded-For", TEST_IP)
            .multiPart("file", "test.txt", "hello".getBytes(), "text/plain")
        .when()
            .post("/api/storage/upload")
        .then()
            .statusCode(400);
    }

    @Test @Order(5)
    void delete_uploadedFile_returns204() {
        assertNotNull(uploadedKey, "depends on upload test");
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .delete("/api/storage/" + uploadedKey)
        .then()
            .statusCode(204);
    }

    @Test @Order(6)
    void upload_rateLimitExceeded_returns429() {
        // Gateway allows 5 uploads/min per IP; the 6th must be throttled.
        // Uses a dedicated email-keyed slot — upload uses IP so isolated by timing.
        int lastStatus = 0;
        for (int i = 0; i < 6; i++) {
            lastStatus = given()
                .header("Authorization", "Bearer " + token)
                .header("X-Forwarded-For", TEST_IP)
                .multiPart("file", "rl.jpg", TINY_JPEG, "image/jpeg")
                .post("/api/storage/upload")
                .statusCode();
        }
        Assertions.assertEquals(429, lastStatus,
            "Expected 429 Too Many Requests on the 6th upload attempt");
    }
}
