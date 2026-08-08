package com.uberlite.surgepricing.api;

import com.uberlite.common.dto.SurgeMultiplierDto;
import com.uberlite.surgepricing.domain.SurgeComputationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for surge pricing operations.
 * <p>
 * Supports:
 * - GET /surge/{h3Cell} → compute and return current surge multiplier
 * - POST /surge/{h3Cell}/pending-request → increment pending request counter
 * - DELETE /surge/{h3Cell}/pending-request → decrement pending request counter
 */
@RestController
@RequestMapping("/surge")
public class SurgeController {
    private final SurgeComputationService surgeComputationService;

    public SurgeController(SurgeComputationService surgeComputationService) {
        this.surgeComputationService = surgeComputationService;
    }

    /**
     * Get the current surge multiplier for an H3 cell.
     *
     * @param h3Cell H3 cell identifier
     * @return SurgeMultiplierDto with h3Cell, multiplier, and updatedAtMs
     */
    @GetMapping("/{h3Cell}")
    public SurgeMultiplierDto getMultiplier(@PathVariable String h3Cell) {
        double multiplier = surgeComputationService.getMultiplier(h3Cell);
        long now = System.currentTimeMillis();
        return new SurgeMultiplierDto(h3Cell, multiplier, now);
    }

    /**
     * Increment the pending request count for an H3 cell.
     * Called by Trip Service when a trip enters the matching pipeline for this cell.
     *
     * @param h3Cell H3 cell identifier
     * @return HTTP 200 on success
     */
    @PostMapping("/{h3Cell}/pending-request")
    public ResponseEntity<Void> incrementPendingRequest(@PathVariable String h3Cell) {
        surgeComputationService.incrementPendingRequest(h3Cell);
        return ResponseEntity.ok().build();
    }

    /**
     * Decrement the pending request count for an H3 cell.
     * Called by Trip Service when a trip leaves the matching pipeline (matched, cancelled, etc.).
     *
     * @param h3Cell H3 cell identifier
     * @return HTTP 200 on success
     */
    @DeleteMapping("/{h3Cell}/pending-request")
    public ResponseEntity<Void> decrementPendingRequest(@PathVariable String h3Cell) {
        surgeComputationService.decrementPendingRequest(h3Cell);
        return ResponseEntity.ok().build();
    }
}
