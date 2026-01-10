package com.crypto.funding.exception;


import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Provider
@Component
@Slf4j
public class JerseyExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        log.error("Uncaught exception in Jersey resource", exception);

        int status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        String errorLabel = "Internal Server Error";
        String message = "An unexpected error occurred. Please try again later.";

        if (exception instanceof WebApplicationException) {
            Response original = ((WebApplicationException) exception).getResponse();
            status = original.getStatus();
            errorLabel = original.getStatusInfo().getReasonPhrase();
            message = exception.getMessage();
        } else if (exception instanceof IllegalArgumentException) {
            status = Response.Status.BAD_REQUEST.getStatusCode();
            errorLabel = "Bad Request";
            message = exception.getMessage();
        }
        // Add more custom exceptions here if needed

        try {
            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(status)
                    .error(errorLabel)
                    .message(message)
                    // .path() is not easily accessible here without injecting UriInfo
                    .build();

            return Response.status(status)
                    .entity(error)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            // Fallback for extreme cases
            return Response.status(500)
                    .entity("{\"error\": \"Critical Error during error handling\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }
}
