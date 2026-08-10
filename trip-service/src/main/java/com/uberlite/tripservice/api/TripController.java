package com.uberlite.tripservice.api;

import com.uberlite.common.dto.RiderTripCountDto;
import com.uberlite.tripservice.api.dto.CreateTripRequest;
import com.uberlite.tripservice.api.dto.TransitionRequest;
import com.uberlite.tripservice.api.dto.TripResponse;
import com.uberlite.tripservice.domain.TripOrchestrator;
import com.uberlite.tripservice.domain.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Rider/driver-facing trip API (ARCHITECTURE.md Sec. 4).
 *
 * <p>Reads go straight to {@link TripService}; anything that changes state goes through
 * {@link TripOrchestrator}, so the downstream calls a transition implies can never be bypassed by
 * calling the "plain" endpoint.
 */
@RestController
@RequestMapping("/trips")
public class TripController {

    private final TripOrchestrator orchestrator;
    private final TripService tripService;

    public TripController(TripOrchestrator orchestrator, TripService tripService) {
        this.orchestrator = orchestrator;
        this.tripService = tripService;
    }

    /**
     * Creates a trip and quotes it in one call.
     *
     * @return {@code 201} with the trip in {@code PRICED}; {@code 502} naming the failed dependency
     *     (and carrying the {@code tripId}) if pricing failed, in which case the trip exists in
     *     {@code REQUESTED} and can be quoted via {@link #requestQuote}
     */
    @PostMapping
    public ResponseEntity<TripResponse> create(@Valid @RequestBody CreateTripRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orchestrator.createAndQuote(request));
    }

    /**
     * Retry path for a trip left in {@code REQUESTED} because Price Estimation was unavailable.
     *
     * @return {@code 200} with the trip in {@code PRICED}; {@code 409} if the trip has already moved
     *     past {@code REQUESTED}; {@code 502} if pricing is still unavailable
     */
    @PostMapping("/{id}/request-quote")
    public TripResponse requestQuote(@PathVariable UUID id) {
        return orchestrator.requestQuote(id);
    }

    /**
     * Retry path for a trip whose match attempt failed because Matching was unavailable. A failed
     * attempt consumes none of the k=3 budget, so this simply resumes the search.
     *
     * @return {@code 200} with the trip in {@code DRIVER_PROPOSED} or {@code UNMATCHED};
     *     {@code 409} if the trip is not waiting for a driver; {@code 502} if Matching is still down
     */
    @PostMapping("/{id}/request-match")
    public TripResponse requestMatch(@PathVariable UUID id) {
        return orchestrator.requestMatch(id);
    }

    @GetMapping("/{id}")
    public TripResponse get(@PathVariable UUID id) {
        return tripService.getTrip(id);
    }

    /**
     * Completed-trip count per rider, for background/analytics consumers.
     *
     * <p>Exists because Discounts Analytics' nightly batch (ARCHITECTURE.md Sec. 2) must not reach
     * into Trip Service's database — "database per service" (Sec. 5) means the aggregate is
     * published as a contract instead. Placed on a distinct path so it can be rate-limited or
     * moved to a read replica later without touching the rider-facing routes.
     */
    @GetMapping("/rider-trip-counts")
    public List<RiderTripCountDto> riderTripCounts() {
        return tripService.countCompletedTripsPerRider();
    }

    /**
     * Applies a state transition and any orchestration it implies — {@code ACCEPTED_BY_RIDER} and
     * {@code DRIVER_DECLINED} trigger matching, terminal states release the surge demand signal.
     *
     * @return the trip in its <em>resulting</em> state, which for the auto-transitioning cases is
     *     one step further on than the requested {@code toState}
     */
    @PostMapping("/{id}/transition")
    public TripResponse transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        return orchestrator.transition(id, request);
    }
}
