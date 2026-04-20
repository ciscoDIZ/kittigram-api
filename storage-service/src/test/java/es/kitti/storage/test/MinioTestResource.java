package es.kitti.storage.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class MinioTestResource implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> minio;

    @Override
    public Map<String, String> start() {
        minio = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
                .withExposedPorts(9000)
                .withEnv("MINIO_ROOT_USER", "kitties")
                .withEnv("MINIO_ROOT_PASSWORD", "kitties123")
                .withCommand("server /data");
        minio.start();

        String endpoint = "http://localhost:" + minio.getMappedPort(9000);

        // Crear el bucket
        try {
            minio.execInContainer(
                    "sh", "-c",
                    "mc alias set local http://localhost:9000 kitties kitties123 && mc mb local/kitties"
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test bucket", e);
        }

        return Map.of(
                "quarkus.s3.endpoint-override", endpoint,
                "quarkus.s3.aws.credentials.static-provider.access-key-id", "kitties",
                "quarkus.s3.aws.credentials.static-provider.secret-access-key", "kitties123"
        );
    }

    @Override
    public void stop() {
        if (minio != null) {
            minio.stop();
        }
    }
}