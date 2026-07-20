package com.cashflow.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised exception-to-HTTP-response mapping for all REST controllers.
 *
 * <p>Rather than letting Spring's default handler emit a 500 for every
 * unhandled exception, we intercept known domain errors here and map them
 * to sensible HTTP status codes with a structured JSON body. This way the
 * client always gets a parseable error, not an HTML stack trace.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles domain validation failures from the service layer.
     *
     * <p>LedgerService throws IllegalArgumentException for things like negative
     * amounts, self-transfers, and null transaction fields. Those are the
     * client's fault, so we return 400 rather than 500.</p>
     *
     * @param ex the validation exception
     * @return 400 Bad Request with a structured JSON error body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Rejected request due to validation failure: {}", ex.getMessage());

        // LinkedHashMap preserves insertion order for consistent JSON key ordering
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Catch-all for any unexpected runtime exception.
     *
     * <p>We log the full stack trace here (not just the message) because
     * these are genuine bugs, not client errors. The response deliberately
     * omits internal details to avoid leaking implementation info.</p>
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericError(Exception ex) {
        log.error("Unhandled exception in controller layer", ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred. Check server logs.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
