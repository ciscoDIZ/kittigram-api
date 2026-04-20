package es.kitti.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import es.kitti.e2e.support.E2EConfig;
import es.kitti.e2e.support.MailHogClient;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdoptionJourneyE2E {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TS = System.currentTimeMillis();

    private static final String ADOPTER_EMAIL = "adopter_" + TS + "@e2e.test";
    private static final String ADOPTER_PASSWORD = "Password1!";
    private static final String ORG_EMAIL = "org_" + TS + "@e2e.test";
    private static final String ORG_PASSWORD = "Password1!";

    // State shared across ordered steps
    private static String adopterToken;
    private static String adopterRefreshToken;
    private static String orgToken;
    private static Long orgId;
    private static Long catId;
    private static Long adoptionId;

    @BeforeAll
    static void setup() {
        E2EConfig.waitForStack();
        MailHogClient.deleteAll();
    }

    @Test @Order(1)
    void registerAdopter() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "email", ADOPTER_EMAIL,
                "password", ADOPTER_PASSWORD,
                "name", "Test",
                "surname", "Adopter",
                "role", "User"
            ))
        .when()
            .post("/api/users")
        .then()
            .statusCode(201);
    }

    @Test @Order(2)
    void registerOrganization() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "email", ORG_EMAIL,
                "password", ORG_PASSWORD,
                "name", "Test",
                "surname", "Organization",
                "role", "Organization"
            ))
        .when()
            .post("/api/users")
        .then()
            .statusCode(201);
    }

    @Test @Order(3)
    void activateAdopter() {
        String emailBody = MailHogClient.waitForEmail(ADOPTER_EMAIL);
        String token = MailHogClient.extractActivationToken(emailBody);
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("token", token))
        .when()
            .post("/api/users/activate")
        .then()
            .statusCode(200);
    }

    @Test @Order(4)
    void activateOrganization() {
        String emailBody = MailHogClient.waitForEmail(ORG_EMAIL);
        String token = MailHogClient.extractActivationToken(emailBody);
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("token", token))
        .when()
            .post("/api/users/activate")
        .then()
            .statusCode(200);
    }

    @Test @Order(5)
    void loginAdopter() {
        Response resp = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", ADOPTER_EMAIL, "password", ADOPTER_PASSWORD))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .extract().response();

        adopterToken = resp.jsonPath().getString("accessToken");
        adopterRefreshToken = resp.jsonPath().getString("refreshToken");
    }

    @Test @Order(6)
    void loginOrganization() throws Exception {
        Response resp = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", ORG_EMAIL, "password", ORG_PASSWORD))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .extract().response();

        orgToken = resp.jsonPath().getString("accessToken");
        orgId = extractSubFromJwt(orgToken);
    }

    @Test @Order(7)
    void createCat() {
        catId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + orgToken)
            .body(Map.of(
                "name", "Luna",
                "age", 2,
                "sex", "Female",
                "description", "Friendly tabby",
                "neutered", true,
                "city", "Madrid",
                "country", "Spain"
            ))
        .when()
            .post("/api/cats")
        .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        assertNotNull(catId);
    }

    @Test @Order(8)
    void searchCats_public() {
        given()
        .when()
            .get("/api/cats")
        .then()
            .statusCode(200)
            .body("$", not(empty()));
    }

    @Test @Order(9)
    void createAdoptionRequest() {
        adoptionId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adopterToken)
            .body(Map.of(
                "catId", catId,
                "organizationId", orgId
            ))
        .when()
            .post("/api/adoptions")
        .then()
            .statusCode(201)
            .extract().jsonPath().getLong("id");

        assertNotNull(adoptionId);
    }

    @Test @Order(10)
    void adopterListsOwnAdoptions() {
        given()
            .header("Authorization", "Bearer " + adopterToken)
        .when()
            .get("/api/adoptions/my")
        .then()
            .statusCode(200)
            .body("id", hasItem(adoptionId.intValue()));
    }

    @Test @Order(11)
    void orgListsReceivedAdoptions() {
        given()
            .header("Authorization", "Bearer " + orgToken)
        .when()
            .get("/api/adoptions/organization")
        .then()
            .statusCode(200)
            .body("id", hasItem(adoptionId.intValue()));
    }

    @Test @Order(12)
    void adopterViewsOwnAdoption() {
        given()
            .header("Authorization", "Bearer " + adopterToken)
        .when()
            .get("/api/adoptions/" + adoptionId)
        .then()
            .statusCode(200)
            .body("id", equalTo(adoptionId.intValue()));
    }

    @Test @Order(13)
    void orgViewsAdoption() {
        given()
            .header("Authorization", "Bearer " + orgToken)
        .when()
            .get("/api/adoptions/" + adoptionId)
        .then()
            .statusCode(200)
            .body("id", equalTo(adoptionId.intValue()));
    }

    @Test @Order(14)
    void submitScreeningForm() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adopterToken)
            .body(Map.ofEntries(
                Map.entry("hasPreviousCatExperience", true),
                Map.entry("previousPetsHistory", "Had two cats before"),
                Map.entry("adultsInHousehold", 2),
                Map.entry("hasChildren", false),
                Map.entry("hasOtherPets", false),
                Map.entry("hoursAlonePerDay", 4),
                Map.entry("stableHousing", true),
                Map.entry("housingType", "Apartment"),
                Map.entry("housingSize", 70),
                Map.entry("hasOutdoorAccess", false),
                Map.entry("isRental", true),
                Map.entry("rentalPetsAllowed", true),
                Map.entry("hasWindowsWithView", true),
                Map.entry("hasVerticalSpace", true),
                Map.entry("hasHidingSpots", true),
                Map.entry("householdActivityLevel", "Moderate"),
                Map.entry("whyCatsNeedToPlay", "For mental stimulation and health"),
                Map.entry("dailyPlayMinutes", 30),
                Map.entry("plannedEnrichment", "Toys, scratching posts, cat trees"),
                Map.entry("reactionToUnwantedBehavior", "Redirect with toys, never punish"),
                Map.entry("hasScratchingPost", true),
                Map.entry("willingToEnrichEnvironment", true),
                Map.entry("motivationToAdopt", "Want to give a cat a loving home"),
                Map.entry("understandsLongTermCommitment", true),
                Map.entry("hasVetBudget", true),
                Map.entry("allHouseholdMembersAgree", true),
                Map.entry("anyoneHasAllergies", false)
            ))
        .when()
            .post("/api/adoptions/" + adoptionId + "/form")
        .then()
            .statusCode(201);
    }

    @Test @Order(15)
    void orgAcceptsAdoption() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + orgToken)
            .body(Map.of("status", "Accepted"))
        .when()
            .patch("/api/adoptions/" + adoptionId + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("Accepted"));
    }

    @Test @Order(16)
    void scheduleInterview() {
        String scheduledAt = LocalDateTime.now().plusDays(7)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + orgToken)
            .body(Map.of(
                "scheduledAt", scheduledAt,
                "notes", "Please bring ID"
            ))
        .when()
            .post("/api/adoptions/" + adoptionId + "/interview")
        .then()
            .statusCode(201);
    }

    @Test @Order(17)
    void submitLegalContract() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adopterToken)
            .body(Map.of(
                "fullName", "Test Adopter",
                "idNumber", "12345678A",
                "phone", "600000000",
                "address", "Calle Mayor 1",
                "city", "Madrid",
                "postalCode", "28001",
                "acceptsVetVisits", true,
                "acceptsFollowUpContact", true,
                "acceptsReturnIfNeeded", true,
                "acceptsTermsAndConditions", true
            ))
        .when()
            .post("/api/adoptions/" + adoptionId + "/adoption-form")
        .then()
            .statusCode(201);
    }

    @Test @Order(18)
    void refreshAdopterToken() {
        Response resp = given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", adopterRefreshToken))
        .when()
            .post("/api/auth/refresh")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .extract().response();

        // Store new tokens, keep old refresh token for next test
        String oldRefresh = adopterRefreshToken;
        adopterToken = resp.jsonPath().getString("accessToken");
        adopterRefreshToken = resp.jsonPath().getString("refreshToken");
        // Store old one back for the rejection test (we need it)
        AdoptionJourneyE2E.oldRefreshToken = oldRefresh;
    }

    private static String oldRefreshToken;

    @Test @Order(19)
    void oldRefreshTokenRejected() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", oldRefreshToken))
        .when()
            .post("/api/auth/refresh")
        .then()
            .statusCode(401);
    }

    @Test @Order(20)
    void logout() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", adopterRefreshToken))
        .when()
            .post("/api/auth/logout")
        .then()
            .statusCode(204);
    }

    private static Long extractSubFromJwt(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode node = MAPPER.readTree(payload);
        return node.get("sub").asLong();
    }
}
