package com.uberlite.driverdiscovery.api;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.LocationDto;
import com.uberlite.driverdiscovery.api.dto.StatusUpdateRequest;
import com.uberlite.driverdiscovery.domain.DriverStoreService;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for driver discovery operations.
 * <p>
 * Lightweight controller that delegates persistence and query logic to
 * DriverStoreService.
 */
@RestController
@RequestMapping("/drivers")
public class DriverController {
    private final DriverStoreService store;

    public DriverController(DriverStoreService store) {
        this.store = store;
    }

    /**
     * Accept a location update for the given driver and persist it.
     *
     * @param driverId id of the driver being updated
     * @param loc location payload containing latitude and longitude
     * @return HTTP 200 on success with an empty body
     */
    @PostMapping("/{driverId}/location")
    public ResponseEntity<Void> updateLocation(@PathVariable String driverId, @RequestBody LocationDto loc) {
        store.updateLocation(driverId, loc);
        return ResponseEntity.ok().build();
    }

    /**
     * Update the availability status of a driver.
     *
     * @param driverId id of the driver
     * @param request wrapper containing the new status value
     * @return HTTP 200 on success
     */
    @PostMapping("/{driverId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable String driverId, @Valid @RequestBody StatusUpdateRequest request) {
        store.setStatus(driverId, request.getStatus());
        return ResponseEntity.ok().build();
    }

    /**
     * Return a list of nearby drivers within the given radius ordered by distance.
     *
     * @param lat search center latitude
     * @param lon search center longitude
     * @param radiusMeters radius in meters to search within
     * @param limit maximum number of results
     * @return list of nearby driver candidates
     */
    @GetMapping("/nearby")
    public List<DriverCandidateDto> nearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam double radiusMeters,
            @RequestParam(defaultValue = "10") int limit) {
        return store.nearby(lat, lon, radiusMeters, limit);
    }

    /**
     * Return drivers whose H3 cell is within the k-ring of the provided cell.
     *
     * @param h3Cell central H3 cell id
     * @param kRing H3 k-ring radius
     * @param limit maximum number of results
     * @return list of driver candidates inside the specified H3 area
     */
    @GetMapping("/nearby-by-cell")
    public List<DriverCandidateDto> nearbyByCell(
            @RequestParam String h3Cell, @RequestParam int kRing, @RequestParam(defaultValue = "10") int limit) {
        return store.nearbyByCell(h3Cell, kRing, limit);
    }
}
