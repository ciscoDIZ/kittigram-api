package org.ciscoadiz.adoption.resource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import org.ciscoadiz.adoption.test.KafkaTestResource;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
class AdoptionResourceTest {

    @Test
    void testCreateAdoptionRequestUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "catId": 1,
                    "organizationId": 1
                }
                """)
                .when()
                .post("/adoptions")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "1", roles = "User")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kittigram.org")
    })
    void testCreateAdoptionRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "catId": 1,
                    "organizationId": 2
                }
                """)
                .when()
                .post("/adoptions")
                .then()
                .statusCode(201)
                .body("catId", equalTo(1))
                .body("adopterId", equalTo(1))
                .body("status", equalTo("Pending"));
    }

    @Test
    void testFindByIdNotFound() {
        given()
                .when()
                .get("/adoptions/999999")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "1", roles = "User")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kittigram.org")
    })
    void testFindByIdNotFoundAuthenticated() {
        given()
                .when()
                .get("/adoptions/999999")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "100", roles = "User")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "100"),
            @Claim(key = "email", value = "adopter@kittigram.org")
    })
    void testFindMyAdoptions() {
        given()
                .when()
                .get("/adoptions/my")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "200", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "200"),
            @Claim(key = "email", value = "org@kittigram.org")
    })
    void testFindByOrganization() {
        given()
                .when()
                .get("/adoptions/organization")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kittigram.org")
    })
    void testUpdateStatusNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body("""
            {
                "status": "Accepted",
                "reason": null
            }
            """)
                .when()
                .patch("/adoptions/999999/status")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "1", roles = "User")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "adopter@kittigram.org")
    })
    void testFindByIdAccessibleByAdopter() {
        Integer id = given()
                .contentType(ContentType.JSON)
                .body("""
                { "catId": 77, "organizationId": 2 }
                """)
                .when()
                .post("/adoptions")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .when()
                .get("/adoptions/" + id)
                .then()
                .statusCode(200)
                .body("adopterId", equalTo(1));
    }

    @Test
    @TestSecurity(user = "1", roles = "User")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kittigram.org")
    })
    void testSubmitRequestFormNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body("""
            {
                "hasPreviousCatExperience": true,
                "adultsInHousehold": 2,
                "hasChildren": false,
                "hasOtherPets": false,
                "hoursAlonePerDay": 8,
                "stableHousing": true,
                "housingType": "Apartment",
                "housingSize": 60,
                "hasOutdoorAccess": false,
                "isRental": false,
                "hasWindowsWithView": true,
                "hasVerticalSpace": true,
                "hasHidingSpots": true,
                "householdActivityLevel": "Quiet",
                "whyCatsNeedToPlay": "instinct",
                "dailyPlayMinutes": 30,
                "plannedEnrichment": "toys",
                "reactionToUnwantedBehavior": "ignore",
                "hasScratchingPost": true,
                "willingToEnrichEnvironment": true,
                "motivationToAdopt": "love cats",
                "understandsLongTermCommitment": true,
                "hasVetBudget": true,
                "allHouseholdMembersAgree": true,
                "anyoneHasAllergies": false
            }
            """)
                .when()
                .post("/adoptions/999999/form")
                .then()
                .statusCode(404);
    }
}