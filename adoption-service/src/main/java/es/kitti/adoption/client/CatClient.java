package es.kitti.adoption.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "cat-service")
@Path("/cats")
@Produces(MediaType.APPLICATION_JSON)
public interface CatClient {

    @GET
    @Path("/{id}")
    Uni<Response> findById(@PathParam("id") Long id);
}