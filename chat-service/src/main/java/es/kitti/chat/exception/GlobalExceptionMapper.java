package es.kitti.chat.exception;

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

        if (exception instanceof ConversationNotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(404, exception.getMessage()))
                    .build();
        }
        if (exception instanceof ConversationAlreadyExistsException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(409, exception.getMessage()))
                    .build();
        }
        if (exception instanceof UserBlockedException) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(403, exception.getMessage()))
                    .build();
        }
        if (exception instanceof ForbiddenException) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse(403, "Access denied"))
                    .build();
        }
        if (exception instanceof jakarta.ws.rs.NotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(404, exception.getMessage()))
                    .build();
        }

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse(500, "An unexpected error occurred"))
                .build();
    }
}