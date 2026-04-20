package es.kitti.gateway.ratelimit;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class RateLimitExceptionMapper implements ExceptionMapper<RateLimitExceededException> {

    @Override
    public Response toResponse(RateLimitExceededException exception) {
        return Response.status(429)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "Too many requests, please try again later"))
                .build();
    }
}
