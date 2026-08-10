package com.uberlite.matching.domain;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.dto.MatchRequestDto;
import com.uberlite.common.dto.RouteEstimateDto;
import com.uberlite.matching.client.DriverDiscoveryClient;
import com.uberlite.matching.client.RouteServiceClient;
import com.uberlite.matching.config.MatchingProperties;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Greedy nearest-available-driver matching (ARCHITECTURE.md Sec. 2, "MS"; paper Sec. 4.1).
 *
 * <p>Flow: ask Driver Discovery for candidates around the pickup, price each one with a
 * Route Service distance + local straight-line ETA, return the lowest-ETA candidate.
 *
 * <p><b>Stateless by design.</b> This service does not remember which drivers already declined a
 * trip. Trip Service owns the retry budget (k=3) and the declined-driver list on the trip's
 * Postgres row, and re-calls {@code /matches} with the same {@code tripId}.
 *
 * <p><b>Swap-out point.</b> The paper specifies batch-optimal assignment over (rider, driver, route)
 * triplets. That replaces {@link #findBestMatch} wholesale: candidates would be buffered over a
 * short window and solved as a min-cost bipartite assignment instead of picked greedily per request.
 * Nothing outside this class needs to change — the {@code POST /matches} contract is identical.
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final DriverDiscoveryClient driverDiscoveryClient;
    private final RouteServiceClient routeServiceClient;
    private final MatchingProperties properties;
    private final PickupEtaCalculator etaCalculator;

    public MatchingService(DriverDiscoveryClient driverDiscoveryClient,
                           RouteServiceClient routeServiceClient,
                           MatchingProperties properties) {
        this.driverDiscoveryClient = driverDiscoveryClient;
        this.routeServiceClient = routeServiceClient;
        this.properties = properties;
        this.etaCalculator =
                new PickupEtaCalculator(properties.getAverageSpeedMps(), properties.getDefaultDetourFactor());
    }

    /**
     * @return the candidate with the lowest pickup ETA
     * @throws NoDriversAvailableException if Driver Discovery returned no usable candidate (-> 404)
     * @throws DependencyFailedException if a downstream service was unreachable (-> 502)
     */
    public DriverCandidateDto findBestMatch(MatchRequestDto request) {
        LocationDto pickup = request.getPickup();
        List<DriverCandidateDto> candidates = fetchCandidates(request.getTripId(), pickup);

        if (candidates.isEmpty()) {
            throw new NoDriversAvailableException(request.getTripId());
        }

        List<DriverCandidateDto> ranked = rank(request.getTripId(), pickup, candidates);
        if (ranked.isEmpty()) {
            // Candidates existed but we could not score a single one. That is a Route Service
            // problem, not an empty marketplace — a 404 here would lie to Trip Service.
            throw new DependencyFailedException(
                    "route-service",
                    "Could not compute a pickup ETA for any of the " + candidates.size()
                            + " candidates for trip " + request.getTripId(),
                    null);
        }

        DriverCandidateDto best = ranked.stream()
                // Tie-break on driverId so the choice is deterministic and tests aren't flaky.
                .min(Comparator.comparingLong(DriverCandidateDto::getEtaSeconds)
                        .thenComparing(DriverCandidateDto::getDriverId))
                .orElseThrow();

        log.info("Trip {}: proposing driver {} ({}s pickup ETA) out of {} candidate(s)",
                request.getTripId(), best.getDriverId(), best.getEtaSeconds(), ranked.size());
        return best;
    }

    private List<DriverCandidateDto> fetchCandidates(String tripId, LocationDto pickup) {
        try {
            List<DriverCandidateDto> candidates = driverDiscoveryClient.nearby(
                    pickup.getLat(), pickup.getLon(),
                    properties.getRadiusMeters(), properties.getCandidateLimit());
            return candidates == null ? List.of() : candidates;
        } catch (FeignException e) {
            // Only Feign-level failures are a dependency problem. A bug in our own code must not be
            // reported as "Driver Discovery is down" — it propagates and becomes an honest 500.
            throw new DependencyFailedException(
                    "driver-discovery-service",
                    "Could not fetch nearby drivers for trip " + tripId, e);
        }
    }

    /**
     * Scores each candidate with a Route Service distance. A candidate that cannot be scored is
     * skipped rather than failing the whole match: one bad row must not deny the rider a driver.
     */
    private List<DriverCandidateDto> rank(String tripId, LocationDto pickup, List<DriverCandidateDto> candidates) {
        List<DriverCandidateDto> ranked = new ArrayList<>(candidates.size());
        for (DriverCandidateDto candidate : candidates) {
            if (candidate == null || candidate.getDriverId() == null || candidate.getLocation() == null) {
                log.warn("Trip {}: skipping candidate with no id/location: {}", tripId, candidate);
                continue;
            }
            try {
                RouteEstimateDto route = routeServiceClient.estimate(
                        candidate.getLocation().getLat(), candidate.getLocation().getLon(),
                        pickup.getLat(), pickup.getLon());
                long etaSeconds =
                        etaCalculator.etaSeconds(route.getStraightDistanceKm(), route.getDetourFactor());
                // Re-emit with our own ETA: the value DRS returns is its own estimate, not a
                // pickup ETA for this rider, and must not be trusted for ranking.
                ranked.add(new DriverCandidateDto(candidate.getDriverId(), candidate.getLocation(), etaSeconds));
            } catch (FeignException | IllegalArgumentException e) {
                // FeignException: Route Service failed for this candidate.
                // IllegalArgumentException: Route Service returned a nonsensical distance.
                // Anything else is our own bug and is allowed to propagate rather than be swallowed
                // once per candidate and resurface as a misleading 502.
                log.warn("Trip {}: skipping candidate {} — pickup ETA unavailable: {}",
                        tripId, candidate.getDriverId(), e.toString());
            }
        }
        return ranked;
    }
}



