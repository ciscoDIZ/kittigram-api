package es.kitti.chat.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ChatInternalResourceTest {

    private static final String INTERNAL_TOKEN = "test-internal-secret";

    @Test
    void createConversation_missingToken_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "intakeRequestId": 1, "userId": 100, "organizationId": 200 }
                        """)
                .when()
                .post("/chats/internal/conversations")
                .then()
                .statusCode(401)
                .body("message", containsString("internal token"));
    }

    @Test
    void createConversation_wrongToken_returns401() {
        given()
                .header("X-Internal-Token", "nope")
                .contentType(ContentType.JSON)
                .body("""
                        { "intakeRequestId": 1, "userId": 100, "organizationId": 200 }
                        """)
                .when()
                .post("/chats/internal/conversations")
                .then()
                .statusCode(401);
    }

    @Test
    void createConversation_validToken_returns201() {
        int intakeId = uniqueIntakeId();
        given()
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(ContentType.JSON)
                .body(String.format("""
                        { "intakeRequestId": %d, "userId": 100, "organizationId": 200 }
                        """, intakeId))
                .when()
                .post("/chats/internal/conversations")
                .then()
                .statusCode(201)
                .body("intakeRequestId", equalTo(intakeId))
                .body("userId", equalTo(100))
                .body("organizationId", equalTo(200))
                .body("id", notNullValue());
    }

    @Test
    void createConversation_duplicate_returns409() {
        int intakeId = uniqueIntakeId();
        String body = String.format("""
                { "intakeRequestId": %d, "userId": 100, "organizationId": 200 }
                """, intakeId);

        given()
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(ContentType.JSON).body(body)
                .when().post("/chats/internal/conversations")
                .then().statusCode(201);

        given()
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(ContentType.JSON).body(body)
                .when().post("/chats/internal/conversations")
                .then().statusCode(409);
    }

    private static int uniqueIntakeId() {
        return (int) (System.nanoTime() & 0x7FFFFFFFL);
    }
}