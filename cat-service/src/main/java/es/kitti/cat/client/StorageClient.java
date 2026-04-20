package es.kitti.cat.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import es.kitti.cat.client.dto.StorageResponse;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@RegisterRestClient(configKey = "storage-service")
@Path("/storage")
public interface StorageClient {

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<StorageResponse> upload(@RestForm("file") FileUpload file);

    @DELETE
    @Path("/{key}")
    Uni<Void> delete(@PathParam("key") String key);
}