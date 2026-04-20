package es.kitti.auth.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static io.quarkus.arc.ComponentsProvider.LOG;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        LOG.errorf(exception, "Exception caught: %s", exception.getMessage());
        if (exception instanceof InvalidCredentialsException) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse(
                            Response.Status.UNAUTHORIZED.getStatusCode(),
                            exception.getMessage()
                    ))
                    .build();
        }

        if (exception instanceof InvalidTokenException) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse(
                            Response.Status.UNAUTHORIZED.getStatusCode(),
                            exception.getMessage()
                    ))
                    .build();
        }

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(
                        Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        "An unexpected error occurred"
                ))
                .build();
    }
}