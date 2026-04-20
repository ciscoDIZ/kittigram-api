package es.kitti.storage.resource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import es.kitti.storage.test.MinioTestResource;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(MinioTestResource.class)
class StorageResourceTest {

    @Test
    void testUploadImage() throws IOException {
        File tempFile = File.createTempFile("test", ".jpg");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(new byte[100]);
        }
        tempFile.deleteOnExit();

        given()
                .multiPart("file", tempFile, "image/jpeg")
                .when()
                .post("/storage/upload")
                .then()
                .statusCode(200)
                .body("key", notNullValue())
                .body("url", notNullValue());
    }

    @Test
    void testUploadInvalidFileType() throws IOException {
        File tempFile = File.createTempFile("test", ".pdf");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(new byte[100]);
        }
        tempFile.deleteOnExit();

        given()
                .multiPart("file", tempFile, "application/pdf")
                .when()
                .post("/storage/upload")
                .then()
                .statusCode(400);
    }
}