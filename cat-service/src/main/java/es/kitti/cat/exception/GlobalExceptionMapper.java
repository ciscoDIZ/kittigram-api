package es.kitti.cat.exception;

import io.quarkus.logging.Log;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        Log.errorf(exception, "Exception caught: %s", exception.getMessage());

        if (exception instanceof ForbiddenException) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(
                            Response.Status.FORBIDDEN.getStatusCode(),
                            exception.getMessage()
                    ))
                    .build();
        }

        if (exception instanceof CatNotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(
                            Response.Status.NOT_FOUND.getStatusCode(),
                            exception.getMessage()
                    ))
                    .build();
        }

        if (exception instanceof IllegalArgumentException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(
                            Response.Status.BAD_REQUEST.getStatusCode(),
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