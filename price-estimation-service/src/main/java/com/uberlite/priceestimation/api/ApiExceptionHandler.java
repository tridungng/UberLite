package com.uberlite.priceestimation.api;

import com.uberlite.priceestimation.domain.DependencyFailedException;
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
 * Maps domain failures onto HTTP status codes, mirroring Trip Service's handler so error bodies are
 * consistent across the estate ({@code {"message": ...}}).
 *
 * <p>Note there is deliberately no {@code @ExceptionHandler(Exception.class)}: a bug in this service
 * must not masquerade as a downstream outage. Unhandled exceptions fall through to Spring's default
 * 500, which is the honest answer.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** A required pricing input could not be obtained — we refuse to guess a price. */
    @ExceptionHandler(DependencyFailedException.class)
    public ResponseEntity<Map<String, Object>> handleDependencyFailure(DependencyFailedException ex) {
        log.error("Refusing to quote: dependency {} unavailable", ex.getDependency(), ex);
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
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid price estimate request — " + details));
    }
}


