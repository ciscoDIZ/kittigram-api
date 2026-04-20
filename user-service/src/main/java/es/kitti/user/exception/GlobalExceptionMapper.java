package es.kitti.user.exception;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        LOG.errorf(exception, "Exception caught: %s", exception.getMessage());

        return switch (exception) {
            case InvalidTokenException invalidTokenException -> Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(
                            Response.Status.BAD_REQUEST.getStatusCode(),
                            exception.getMessage()
                    ))
                    .build();
            case UserNotFoundException userNotFoundException -> Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(
                            Response.Status.NOT_FOUND.getStatusCode(),
                            exception.getMessage()
                    ))
                    .build();
            case ForbiddenException forbiddenException -> Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(
                            Response.Status.FORBIDDEN.getStatusCode(),
                            exception.getMessage()
                    ))
                    .build();
            case IllegalArgumentException illegalArgumentException -> Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(
                            Response.Status.CONFLICT.getStatusCode(),
                            exception.getMessage()
                    ))
                    .build();
            default -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(
                            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            "An unexpected error occurred"
                    ))
                    .build();
        };

    }
}