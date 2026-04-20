package es.kitti.storage.provider;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ApplicationScoped
public class S3StorageProvider implements StorageProvider {

    @ConfigProperty(name = "bucket.name")
    String bucketName;

    @ConfigProperty(name = "quarkus.s3.endpoint-override")
    String endpoint;

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @Inject
    S3AsyncClient s3;

    @ConfigProperty(name = "storage.public.url")
    String publicUrl;



    @Override
    public Uni<String> upload(String key, byte[] data, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength((long) data.length)
                .build();

        return Uni.createFrom().completionStage(
                s3.putObject(request, AsyncRequestBody.fromBytes(data))
        ).map(response -> getUrl(key));
    }

    @Override
    public Uni<Void> delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return Uni.createFrom().completionStage(
                s3.deleteObject(request)
        ).replaceWithVoid();
    }

    @Override
    public String getUrl(String key) {
        return publicUrl + "/storage/files/" + key;
    }
}