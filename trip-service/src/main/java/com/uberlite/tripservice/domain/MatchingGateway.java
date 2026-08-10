package com.uberlite.tripservice.domain;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.dto.MatchRequestDto;
import com.uberlite.tripservice.client.MatchingClient;
import com.uberlite.tripservice.config.OrchestrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Trip Service's view of the Matching Service.
 *
 * <p>Two responsibilities the caller must not have to think about:
 *
 * <ol>
 *   <li><b>404 is not a failure.</b> An empty marketplace is a real answer and becomes
 *       {@link Optional#empty()}; anything else (502, timeout, no Eureka instance) becomes a
 *       {@link DependencyFailedException} so the trip is left where it is and can be retried
 *       without burning a matching attempt (ARCHITECTURE.md Sec. 4).
 *   <li><b>Exclusions are ours to own.</b> The declined-driver list is durable state on the trip
 *       row, and we send it with every request so Matching — which is deterministic and
 *       greedy-nearest — picks the best <em>eligible</em> driver instead of re-proposing the one who
 *       just declined.
 * </ol>
 *
 * <p>The response is nevertheless re-checked against the exclusion list before it is accepted. That
 * is not redundant: a Matching instance that predates the {@code excludedDriverIds} field would
 * silently ignore it, and ARCHITECTURE.md Sec. 4 makes Trip Service the component ultimately
 * responsible for never proposing a decliner twice. If that happens we re-ask up to
 * {@link OrchestrationProperties#getMaxProposalsPerAttempt()} times before reporting no driver,
 * rather than looping against a deterministic matcher forever.
 */
@Component
public class MatchingGateway {

    static final String DEPENDENCY = "matching-service";

    private static final Logger log = LoggerFactory.getLogger(MatchingGateway.class);

    private final MatchingClient client;
    private final OrchestrationProperties properties;

    public MatchingGateway(MatchingClient client, OrchestrationProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * @param excludedDriverIds drivers who already declined this trip
     * @return the proposed driver, or empty if no <em>eligible</em> driver is available
     * @throws DependencyFailedException if Matching could not answer at all
     */
    public Optional<DriverCandidateDto> proposeDriver(UUID tripId,
                                                      LocationDto pickup,
                                                      Collection<String> excludedDriverIds) {
        Set<String> excluded = Set.copyOf(excludedDriverIds);
        MatchRequestDto request = new MatchRequestDto(tripId.toString(), pickup, List.copyOf(excluded));

        for (int proposal = 1; proposal <= properties.getMaxProposalsPerAttempt(); proposal++) {
            Optional<DriverCandidateDto> candidate = callMatching(tripId, request);
            if (candidate.isEmpty()) {
                return Optional.empty();
            }
            String driverId = candidate.get().getDriverId();
            if (!excluded.contains(driverId)) {
                return candidate;
            }
            log.warn("Matching ignored the exclusion list and re-proposed declined driver {} for "
                            + "trip {} (proposal {}/{})",
                    driverId, tripId, proposal, properties.getMaxProposalsPerAttempt());
        }

        log.info("No eligible driver for trip {} after {} proposals; all were excluded",
                tripId, properties.getMaxProposalsPerAttempt());
        return Optional.empty();
    }

    private Optional<DriverCandidateDto> callMatching(UUID tripId, MatchRequestDto request) {
        try {
            return Optional.of(
                    RemoteCalls.call(DEPENDENCY, "matching a driver for trip " + tripId,
                            () -> client.match(request)));
        } catch (DependencyFailedException e) {
            if (RemoteCalls.httpStatusOf(e) == HttpStatus.NOT_FOUND.value()) {
                log.info("Matching reports no drivers near the pickup for trip {}", tripId);
                return Optional.empty();
            }
            throw e;
        }
    }
}
