package es.kitti.cat.resource;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import es.kitti.cat.client.StorageClient;
import es.kitti.cat.client.dto.StorageResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CatResourceTest {


    @Test
    void testSearchCatsPublic() {
        given()
                .when()
                .get("/cats")
                .then()
                .statusCode(200);
    }

    @Test
    void testGetCatNotFound() {
        given()
                .when()
                .get("/cats/999999")
                .then()
                .statusCode(404);
    }

    @Test
    void testCreateCatUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Peluso",
                    "age": 2,
                    "sex": "Male",
                    "neutered": true,
                    "city": "La Orotava",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "1", roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es")
    })
    void testCreateCatAsRegularUserForbidden() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Peluso",
                    "age": 2,
                    "sex": "Male",
                    "neutered": true,
                    "city": "La Orotava",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es")
    })
    void testCreateCatAsOrganization() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Peluso",
                    "age": 2,
                    "sex": "Male",
                    "neutered": true,
                    "city": "La Orotava",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(201)
                .body("name", equalTo("Peluso"))
                .body("status", equalTo("Available"));
    }

    @Test
    @TestSecurity(user = "1", roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es")
    })
    void testDeleteCatAsRegularUserForbidden() {
        given()
                .when()
                .delete("/cats/999999")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es")
    })
    void testDeleteCatAsOrganizationNotFound() {
        given()
                .when()
                .delete("/cats/999999")
                .then()
                .statusCode(404);
    }
}