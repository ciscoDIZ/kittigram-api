package es.kitti.adoption.resource;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import es.kitti.adoption.client.CatClient;
import es.kitti.adoption.entity.AdoptionRequest;
import es.kitti.adoption.repository.AdoptionRequestRepository;
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
class AdoptionResourceTest {

    @InjectMock
    @RestClient
    CatClient catClient;

    @Inject
    AdoptionRequestRepository adoptionRequestRepository;

    @BeforeEach
    void mockCatClient() {
        when(catClient.findById(anyLong()))
                .thenReturn(Uni.createFrom().item(Response.ok().build()));
    }

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
            @Claim(key = "email", value = "test@kitti.es")
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
            @Claim(key = "email", value = "test@kitti.es")
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
            @Claim(key = "email", value = "adopter@kitti.es")
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
            @Claim(key = "email", value = "org@kitti.es")
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
            @Claim(key = "email", value = "test@kitti.es")
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
            @Claim(key = "email", value = "adopter@kitti.es")
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
            @Claim(key = "email", value = "test@kitti.es")
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

    @Test
    @TestSecurity(user = "1", roles = "User")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es")
    })
    void testCreateAdoptionRequest_catDeleted_returns409() {
        when(catClient.findById(99L))
                .thenReturn(Uni.createFrom().item(Response.status(404).build()));

        given()
                .contentType(ContentType.JSON)
                .body("""
                { "catId": 99, "organizationId": 2 }
                """)
                .when()
                .post("/adoptions")
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "1", roles = "User")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "adopter@kitti.es")
    })
    void testSubmitRequestForm_catDeleted_returns409() {
        Integer adoptionId = given()
                .contentType(ContentType.JSON)
                .body("""
                { "catId": 66, "organizationId": 2 }
                """)
                .when()
                .post("/adoptions")
                .then()
                .statusCode(201)
                .extract().path("id");

        when(catClient.findById(66L))
                .thenReturn(Uni.createFrom().item(Response.status(404).build()));

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
                .post("/adoptions/" + adoptionId + "/form")
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "2", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "2"),
            @Claim(key = "email", value = "org@kitti.es")
    })
    void testUpdateStatus_catDeleted_returns409() {
        AdoptionRequest adoption = new AdoptionRequest();
        adoption.catId = 55L;
        adoption.adopterId = 1L;
        adoption.organizationId = 2L;
        adoption.adopterEmail = "adopter@kitti.es";
        AdoptionRequest saved = Panache.withTransaction(() -> adoptionRequestRepository.persist(adoption))
                .await().indefinitely();

        when(catClient.findById(55L))
                .thenReturn(Uni.createFrom().item(Response.status(404).build()));

        given()
                .contentType(ContentType.JSON)
                .body("""
                { "status": "Accepted", "reason": null }
                """)
                .when()
                .patch("/adoptions/" + saved.id + "/status")
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "2", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "2"),
            @Claim(key = "email", value = "org@kitti.es")
    })
    void testUpdateStatus_rejected_catDeleted_returns200() {
        AdoptionRequest adoption = new AdoptionRequest();
        adoption.catId = 56L;
        adoption.adopterId = 1L;
        adoption.organizationId = 2L;
        adoption.adopterEmail = "adopter@kitti.es";
        AdoptionRequest saved = Panache.withTransaction(() -> adoptionRequestRepository.persist(adoption))
                .await().indefinitely();

        when(catClient.findById(56L))
                .thenReturn(Uni.createFrom().item(Response.status(404).build()));

        given()
                .contentType(ContentType.JSON)
                .body("""
                { "status": "Rejected", "reason": "Cat no longer available" }
                """)
                .when()
                .patch("/adoptions/" + saved.id + "/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("Rejected"));
    }
}