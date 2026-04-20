package es.kitti.storage.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import es.kitti.storage.dto.UploadResponse;
import es.kitti.storage.service.StorageService;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;

@Path("/storage")
@Produces(MediaType.APPLICATION_JSON)
public class StorageResource {

    @Inject
    StorageService storageService;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<Response> upload(
            @RestForm("file") FileUpload file) throws IOException {

        byte[] data = Files.readAllBytes(file.uploadedFile());
        String contentType = file.contentType();

        return storageService.upload(data, contentType, file.fileName())
                .onItem().transform(url -> {
                    String key = url.substring(url.lastIndexOf("/") + 1);
                    return Response.ok(new UploadResponse(key, url)).build();
                });
    }

    @DELETE
    @Path("/{key}")
    public Uni<Response> delete(@PathParam("key") String key) {
        return storageService.delete(key)
                .onItem().transform(v -> Response.noContent().build());
    }
}