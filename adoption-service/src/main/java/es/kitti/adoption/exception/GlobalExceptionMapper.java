package es.kitti.adoption.exception;

import io.quarkus.logging.Log;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import es.kitti.adoption.intake.exception.IntakeRequestNotFoundException;
import es.kitti.adoption.intake.exception.InvalidIntakeStatusException;

import java.time.LocalDateTime;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        Log.errorf(exception, "Exception caught: %s", exception.getMessage());

        if (exception instanceof AdoptionRequestNotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(404, exception.getMessage()))
                    .build();
        }
        if (exception instanceof InvalidAdoptionStatusException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(409, exception.getMessage()))
                    .build();
        }
        if (exception instanceof AdoptionFormAlreadySubmittedException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(409, exception.getMessage()))
                    .build();
        }
        if (exception instanceof CatNotAvailableException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(409, exception.getMessage()))
                    .build();
        }
        if (exception instanceof IntakeRequestNotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(404, exception.getMessage()))
                    .build();
        }
        if (exception instanceof InvalidIntakeStatusException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(409, exception.getMessage()))
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