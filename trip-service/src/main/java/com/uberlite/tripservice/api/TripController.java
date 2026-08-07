package com.uberlite.tripservice.api;

import com.uberlite.tripservice.api.dto.CreateTripRequest;
import com.uberlite.tripservice.api.dto.TransitionRequest;
import com.uberlite.tripservice.api.dto.TripResponse;
import com.uberlite.tripservice.domain.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/trips")
public class TripController {
    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> create(@Valid @RequestBody CreateTripRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripService.createTrip(request));
    }

    @GetMapping("/{id}")
    public TripResponse get(@PathVariable UUID id) {
        return tripService.getTrip(id);
    }

    @PostMapping("/{id}/transition")
    public TripResponse transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        return tripService.transitionTrip(id, request);
    }
}
