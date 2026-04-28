package es.kitti.adoption.intake.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "organization-service")
@Path("/organizations/internal")
@Produces(MediaType.APPLICATION_JSON)
public interface OrganizationClient {

    @GET
    @Path("/by-region/{region}")
    Uni<List<OrganizationPublicMinimal>> findByRegion(
            @PathParam("region") String region,
            @HeaderParam("X-Internal-Token") String token
    );
}
