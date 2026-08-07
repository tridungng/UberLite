package com.uberlite.driverdiscovery.api;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.LocationDto;
import com.uberlite.driverdiscovery.service.DriverStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/drivers")
public class DriverController {
    private final DriverStoreService store;
    public DriverController(DriverStoreService store) { this.store = store; }

    @PostMapping("/{driverId}/location")
    public ResponseEntity<Void> updateLocation(@PathVariable String driverId, @RequestBody LocationDto loc) {
        store.updateLocation(driverId, loc);
        return ResponseEntity.ok().build();
    }

    public static class StatusUpdate { public String status; }
    @PostMapping("/{driverId}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String driverId, @RequestBody StatusUpdate s) {
        store.setStatus(driverId, s.status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/nearby")
    public List<DriverCandidateDto> nearby(@RequestParam double lat, @RequestParam double lon, @RequestParam double radiusMeters, @RequestParam(defaultValue = "10") int limit) {
        return store.nearby(lat, lon, radiusMeters, limit);
    }

    @GetMapping("/nearby-by-cell")
    public List<DriverCandidateDto> nearbyByCell(@RequestParam String h3Cell, @RequestParam int kRing, @RequestParam(defaultValue = "10") int limit) {
        return store.nearbyByCell(h3Cell, kRing, limit);
    }
}
