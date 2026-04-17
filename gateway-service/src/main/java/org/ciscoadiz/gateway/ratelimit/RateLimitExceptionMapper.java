package org.ciscoadiz.gateway.ratelimit;

import io.smallrye.faulttolerance.api.RateLimitException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class RateLimitExceptionMapper implements ExceptionMapper<RateLimitException> {

    @Override
    public Response toResponse(RateLimitException exception) {
        return Response.status(429)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "Too many requests, please try again later"))
                .build();
    }
}
