package com.example.navan.exception;

import com.example.navan.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates exceptions thrown anywhere in the request path into a consistent
 * {@link ErrorResponse} JSON body with the right HTTP status.
 *
 * <p>Logging policy: expected client errors (4xx) are logged at DEBUG — they are
 * normal and already reported to the caller in the body — while the catch-all 500
 * path logs at ERROR with the full stack trace, because an unexpected server fault
 * is the one thing an operator must be able to find in the logs.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Application-raised error carrying its own status and (optionally) an existing id for 409s. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        log.debug("ApiException {} -> {}", ex.getStatus().value(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                ex.getMessage(),
                ex.getStatus().value(),
                ex.getExistingId()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /** Bean-validation failure on a {@code @Valid} request body → 400 with the first field error. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        log.debug("Validation failed: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(msg, 400, null));
    }

    /** Malformed / unparseable JSON body → 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        String msg = ex.getMostSpecificCause().getMessage();
        log.debug("Malformed request body: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Malformed request: " + msg, 400, null));
    }

    /** Unknown route / missing static resource → 404 (instead of leaking a 500). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        log.debug("No resource for path: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No such resource: " + ex.getResourcePath(), 404, null));
    }

    /** Anything unhandled is a genuine server fault → 500, logged with its stack trace. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception; returning 500", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal server error: " + ex.getMessage(), 500, null));
    }
}
