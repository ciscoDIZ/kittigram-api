package es.kitti.auth.resource;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import es.kitti.auth.grpc.UserServiceClient;
import es.kitti.user.grpc.ValidateCredentialsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AuthResourceTest {

    @InjectMock
    UserServiceClient userServiceClient;

    @BeforeEach
    void setUp() {
        Mockito.when(userServiceClient.validateCredentials("test@kitti.es", "password123"))
                .thenReturn(Uni.createFrom().item(
                        ValidateCredentialsResponse.newBuilder()
                                .setValid(true)
                                .setUserId(1L)
                                .setEmail("test@kitti.es")
                                .build()
                ));

        Mockito.when(userServiceClient.validateCredentials("wrong@kitti.es", "wrongpass"))
                .thenReturn(Uni.createFrom().item(
                        ValidateCredentialsResponse.newBuilder()
                                .setValid(false)
                                .build()
                ));
    }

    @Test
    void testLoginSuccess() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "email": "test@kitti.es",
                    "password": "password123"
                }
                """)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue());
    }

    @Test
    void testLoginInvalidCredentials() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "email": "wrong@kitti.es",
                    "password": "wrongpass"
                }
                """)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void testRefreshWithInvalidToken() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "refreshToken": "invalid-token"
                }
                """)
                .when()
                .post("/auth/refresh")
                .then()
                .statusCode(401);
    }

    @Test
    void testLogoutWithoutToken() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "refreshToken": "invalid-token"
                }
                """)
                .when()
                .post("/auth/logout")
                .then()
                .statusCode(401);
    }
}