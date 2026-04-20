package es.kitti.storage.config;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class BucketInitializer {

    @Inject
    S3AsyncClient s3;

    @ConfigProperty(name = "bucket.name")
    String bucketName;

    void onStart(@Observes StartupEvent ev) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).get();
            Log.infof("Bucket '%s' already exists", bucketName);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchBucketException) {
                try {
                    s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).get();
                    Log.infof("Bucket '%s' created", bucketName);
                } catch (Exception ex) {
                    Log.errorf(ex, "Failed to create bucket '%s'", bucketName);
                }
            } else {
                Log.errorf(e, "Failed to check bucket '%s'", bucketName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
