package es.kitti.cat.resource;

import es.kitti.cat.security.InternalOnly;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/cats/internal")
@Produces(MediaType.APPLICATION_JSON)
@InternalOnly
public class InternalPingResource {

    @GET
    @Path("/ping")
    public String ping() {
        return "{\"status\":\"ok\"}";
    }
}
