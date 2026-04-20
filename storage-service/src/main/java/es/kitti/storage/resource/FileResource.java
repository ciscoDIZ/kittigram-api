package es.kitti.storage.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import es.kitti.storage.provider.StorageProvider;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/storage/files")
public class FileResource {

    @Inject
    S3AsyncClient s3;

    @ConfigProperty(name = "bucket.name")
    String bucketName;

    @GET
    @Path("/{key}")
    public Uni<Response> getFile(@PathParam("key") String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return Uni.createFrom().completionStage(
                s3.getObject(request, AsyncResponseTransformer.toBytes())
        ).onItem().transform(response -> {
            String contentType = response.response().contentType();
            return Response.ok(response.asByteArray())
                    .header("Content-Type", contentType)
                    .build();
        }).onFailure().recoverWithItem(
                Response.status(Response.Status.NOT_FOUND).build()
        );
    }
}