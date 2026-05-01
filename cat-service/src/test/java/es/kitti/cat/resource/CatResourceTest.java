package es.kitti.cat.resource;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import es.kitti.cat.client.AdoptionClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class CatResourceTest {

    @InjectMock
    @RestClient
    AdoptionClient adoptionClient;

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

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es")
    })
    void testDeleteCat_noActiveAdoptions_returns204() {
        Long catId = given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Misifu",
                    "age": 1,
                    "sex": "Female",
                    "neutered": false,
                    "city": "Santa Cruz",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");

        when(adoptionClient.hasActiveRequestsForCat(eq(catId), any()))
                .thenReturn(Uni.createFrom().item(false));

        given()
                .when()
                .delete("/cats/" + catId)
                .then()
                .statusCode(204);

        given()
                .when()
                .get("/cats/" + catId)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es")
    })
    void testDeleteCat_hasActiveAdoptions_returns409() {
        Long catId = given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Ronron",
                    "age": 3,
                    "sex": "Male",
                    "neutered": true,
                    "city": "La Laguna",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");

        when(adoptionClient.hasActiveRequestsForCat(eq(catId), any()))
                .thenReturn(Uni.createFrom().item(true));

        given()
                .when()
                .delete("/cats/" + catId)
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es")
    })
    void testFindMine_excludesDeletedCats() {
        Long catId = given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Garfield",
                    "age": 5,
                    "sex": "Male",
                    "neutered": true,
                    "city": "Adeje",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");

        when(adoptionClient.hasActiveRequestsForCat(eq(catId), any()))
                .thenReturn(Uni.createFrom().item(false));

        given().when().delete("/cats/" + catId).then().statusCode(204);

        given()
                .when()
                .get("/cats/mine")
                .then()
                .statusCode(200)
                .body("id", not(hasItem(catId.intValue())));
    }
}
