package es.kitti.chat.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class ChatResourceTest {

    @Test
    void findMineAsUser_unauthorized_returns401() {
        given()
                .when()
                .get("/chats/mine")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "9001", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "9001")})
    void findMineAsUser_returns200() {
        given()
                .when()
                .get("/chats/mine")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "100", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "100")})
    void findMineAsOrganization_asUser_returns403() {
        given()
                .when()
                .get("/chats/organization")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "9002", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "9002")})
    void findMineAsOrganization_returns200() {
        given()
                .when()
                .get("/chats/organization")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "100", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "100")})
    void listMessages_unknownConversation_returns404() {
        given()
                .when()
                .get("/chats/999999/messages")
                .then()
                .statusCode(404);
    }

    @Test
    void sendMessage_unauthorized_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"hi\"}")
                .when()
                .post("/chats/1/messages")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "100", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "100")})
    void sendMessage_blankContent_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"\"}")
                .when()
                .post("/chats/1/messages")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "100", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "100")})
    void sendMessage_unknownConversation_returns404() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"hello\"}")
                .when()
                .post("/chats/999999/messages")
                .then()
                .statusCode(404);
    }

    @Test
    void block_unauthorized_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/chats/1/block")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "100", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "100")})
    void block_asUser_returns403() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/chats/1/block")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "200", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "200")})
    void block_unknownConversation_returns404() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"abuse\"}")
                .when()
                .post("/chats/999999/block")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "200", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "200")})
    void unblock_unknownConversation_returns404() {
        given()
                .when()
                .delete("/chats/999999/block")
                .then()
                .statusCode(404);
    }
}