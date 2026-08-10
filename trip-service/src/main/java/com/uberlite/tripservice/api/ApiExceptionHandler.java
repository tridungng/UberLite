package com.uberlite.tripservice.api;

import com.uberlite.tripservice.domain.DependencyFailedException;
import com.uberlite.tripservice.domain.IllegalTransitionException;
import com.uberlite.tripservice.domain.OrchestratorOwnedStateException;
import com.uberlite.tripservice.domain.TripDependencyFailedException;
import com.uberlite.tripservice.domain.TripNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps domain failures onto HTTP status codes, matching price-estimation-service and
 * matching-service so error bodies are consistent across the estate ({@code {"message": ...}}).
 *
 * <p>There is deliberately no {@code @ExceptionHandler(Exception.class)}: a bug in this service must
 * not masquerade as a downstream outage.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(TripNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalTransition(IllegalTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    /**
     * The caller tried to assert a state that only a downstream answer can justify. A 409 with the
     * endpoint that <em>does</em> produce it, rather than a silent success that would create a
     * driverless {@code DRIVER_PROPOSED} trip.
     */
    @ExceptionHandler(OrchestratorOwnedStateException.class)
    public ResponseEntity<Map<String, Object>> handleOrchestratorOwnedState(OrchestratorOwnedStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    /**
     * A downstream service could not answer. The trip is untouched and the call is safe to repeat,
     * so this is a 502 rather than a 500 — and it names the dependency and the trip so the caller
     * can retry the right thing instead of creating a duplicate trip.
     */
    @ExceptionHandler(DependencyFailedException.class)
    public ResponseEntity<Map<String, Object>> handleDependencyFailure(DependencyFailedException ex) {
        log.error("Trip orchestration halted: dependency {} unavailable", ex.getDependency(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", ex.getMessage());
        body.put("dependency", ex.getDependency());
        if (ex instanceof TripDependencyFailedException tripFailure) {
            body.put("tripId", tripFailure.getTripId().toString());
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    /**
     * Two writers raced for the same trip — e.g. a rider cancelling while the orchestrator applies a
     * proposed driver. Same meaning as an illegal transition to the caller: re-read and retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        log.warn("Concurrent update to a trip was rejected", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", "The trip was modified concurrently; re-read it and retry."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid trip request — " + details));
    }
}
