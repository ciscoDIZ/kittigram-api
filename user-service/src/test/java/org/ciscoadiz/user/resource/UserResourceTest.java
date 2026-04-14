package org.ciscoadiz.user.resource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.inject.Inject;
import org.ciscoadiz.user.event.UserRegisteredEvent;
import org.ciscoadiz.user.test.InMemoryConnectorLifecycleManager;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                    "email": "test@kittigram.org",
                    "password": "password123",
                    "name": "Test",
                    "surname": "User"
                }
                """)
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .body("email", equalTo("test@kittigram.org"))
                .body("name", equalTo("Test"))
                .body("status", equalTo("Pending"));

        assertEquals(1, sink.received().size());
        assertEquals("test@kittigram.org", sink.received().get(0).getPayload().email());
    }

    @Test
    void testRegisterUserDuplicateEmail() {
        String body = """
            {
                "email": "duplicate@kittigram.org",
                "password": "password123",
                "name": "Test",
                "surname": "User"
            }
            """;

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/users")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/users")
                .then()
                .statusCode(400);
    }

    @Test
    void testGetUserUnauthorized() {
        given()
                .when()
                .get("/users/test@kittigram.org")
                .then()
                .statusCode(401);
    }

    @Test
    void testActivateUserInvalidToken() {
        given()
                .when()
                .get("/users/activate?token=invalid-token")
                .then()
                .statusCode(400);
    }
}