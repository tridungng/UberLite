package com.uberlite.matching.api;

import com.uberlite.matching.domain.DependencyFailedException;
import com.uberlite.matching.domain.NoDriversAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps domain failures onto HTTP status codes, mirroring trip-service and price-estimation-service
 * so error bodies are consistent across the estate ({@code {"message": ...}}).
 *
 * <p>No catch-all {@code Exception} handler on purpose: a bug in this service must not masquerade
 * as "no drivers available".
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Empty marketplace — a real, retryable answer for Trip Service. */
    @ExceptionHandler(NoDriversAvailableException.class)
    public ResponseEntity<Map<String, Object>> handleNoDrivers(NoDriversAvailableException ex) {
        log.info("No match for trip {}", ex.getTripId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", ex.getMessage());
        body.put("tripId", ex.getTripId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /** Downstream outage — explicitly NOT a 404, so Trip Service can tell the two apart. */
    @ExceptionHandler(DependencyFailedException.class)
    public ResponseEntity<Map<String, Object>> handleDependencyFailure(DependencyFailedException ex) {
        log.error("Refusing to match: dependency {} unavailable", ex.getDependency(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", ex.getMessage());
        body.put("dependency", ex.getDependency());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid match request — " + details));
    }
}

