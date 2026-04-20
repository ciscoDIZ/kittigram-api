package es.kitti.user.resource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.inject.Inject;
import es.kitti.user.event.UserRegisteredEvent;
import es.kitti.user.test.InMemoryConnectorLifecycleManager;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
@QuarkusTestResource(InMemoryConnectorLifecycleManager.class)
class UserResourceTest {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Test
    void testRegisterUser() {
        InMemorySink<UserRegisteredEvent> sink = connector.sink("user-registered");

        given()
                .contentType(ContentType.JSON)
                .body("""
            {
                "email": "test-%s@kitti.es",
                "password": "password123",
                "name": "Test",
                "surname": "User"
            }
            """.formatted(System.currentTimeMillis()))
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .body("status", equalTo("Pending"));

        assertFalse(sink.received().isEmpty());
    }

    @Test
    void testRegisterUserDuplicateEmail() {
        String body = """
            {
                "email": "duplicate@kitti.es",
                "password": "password123",
                "name": "Test",
                "surname": "User"
            }
            """;

        given().contentType(ContentType.JSON).body(body)
                .when().post("/users")
                .then().statusCode(201);

        given().contentType(ContentType.JSON).body(body)
                .when().post("/users")
                .then().statusCode(409);
    }

    @Test
    void testGetUserUnauthorized() {
        given()
                .when().get("/users/test@kitti.es")
                .then().statusCode(401);
    }
}