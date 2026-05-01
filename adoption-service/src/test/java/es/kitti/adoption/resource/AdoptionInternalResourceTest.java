package es.kitti.adoption.resource;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import es.kitti.adoption.client.CatClient;
import es.kitti.adoption.test.KafkaTestResource;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
class AdoptionInternalResourceTest {

    @InjectMock
    @RestClient
    CatClient catClient;

    @BeforeEach
    void mockCatClient() {
        when(catClient.findById(anyLong()))
                .thenReturn(Uni.createFrom().item(Response.ok().build()));
    }

    private static final String INTERNAL_TOKEN = "test-internal-secret";

    @Test
    void testHasActiveRequests_missingToken_returns401() {
        given()
                .when()
                .get("/adoptions/internal/cats/1/active")
                .then()
                .statusCode(401);
    }

    @Test
    void testHasActiveRequests_wrongToken_returns401() {
        given()
                .header("X-Internal-Token", "wrong-token")
                .when()
                .get("/adoptions/internal/cats/1/active")
                .then()
                .statusCode(401);
    }

    @Test
    void testHasActiveRequests_noCatAdoptions_returnsFalse() {
        given()
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .when()
                .get("/adoptions/internal/cats/999999/active")
                .then()
                .statusCode(200)
                .body(equalTo("false"));
    }

    @Test
    @TestSecurity(user = "1", roles = "User")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "adopter@kitti.es")
    })
    void testHasActiveRequests_withPendingAdoption_returnsTrue() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "catId": 42,
                    "organizationId": 10
                }
                """)
                .when()
                .post("/adoptions")
                .then()
                .statusCode(201);

        given()
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .when()
                .get("/adoptions/internal/cats/42/active")
                .then()
                .statusCode(200)
                .body(equalTo("true"));
    }
}
