package com.uberlite.tripservice.domain;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.dto.MatchRequestDto;
import com.uberlite.tripservice.client.MatchingClient;
import com.uberlite.tripservice.config.OrchestrationProperties;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the two things Trip Service must get right about Matching: the 404/502 split, and
 * the fact that exclusions are ours to enforce because Matching is stateless.
 */
class MatchingGatewayTest {

    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final LocationDto PICKUP = new LocationDto(37.7749, -122.4194);

    private final OrchestrationProperties properties = new OrchestrationProperties();

    @Test
    @DisplayName("returns the proposed driver when nobody is excluded")
    void proposesTheFirstDriver() {
        MatchingGateway gateway = gatewayReturning(() -> driver("driver-1"));

        Optional<DriverCandidateDto> proposed = gateway.proposeDriver(TRIP_ID, PICKUP, List.of());

        assertThat(proposed).isPresent();
        assertThat(proposed.get().getDriverId()).isEqualTo("driver-1");
    }

    @Test
    @DisplayName("skips a driver who already declined and takes the next proposal")
    void skipsExcludedDriverAndRetries() {
        Deque<Supplier<DriverCandidateDto>> answers = new ArrayDeque<>(List.of(
                () -> driver("driver-1"),
                () -> driver("driver-2")));
        AtomicInteger calls = new AtomicInteger();
        MatchingGateway gateway = gatewayReturning(() -> {
            calls.incrementAndGet();
            return answers.pop().get();
        });

        Optional<DriverCandidateDto> proposed = gateway.proposeDriver(TRIP_ID, PICKUP, List.of("driver-1"));

        assertThat(proposed).isPresent();
        assertThat(proposed.get().getDriverId()).isEqualTo("driver-2");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("gives up after maxProposalsPerAttempt when Matching keeps re-proposing a decliner")
    void givesUpWhenMatchingKeepsProposingTheSameExcludedDriver() {
        properties.setMaxProposalsPerAttempt(2);
        AtomicInteger calls = new AtomicInteger();
        MatchingGateway gateway = gatewayReturning(() -> {
            calls.incrementAndGet();
            return driver("driver-1");
        });

        assertThat(gateway.proposeDriver(TRIP_ID, PICKUP, List.of("driver-1"))).isEmpty();
        // Bounded: a greedy, deterministic matcher must not spin forever.
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("404 means an empty marketplace, not a failure")
    void notFoundBecomesEmpty() {
        MatchingGateway gateway = gatewayThrowing(feignException(404));

        assertThat(gateway.proposeDriver(TRIP_ID, PICKUP, List.of())).isEmpty();
    }

    @Test
    @DisplayName("502 is a dependency failure, so the trip is never parked in UNMATCHED for an outage")
    void badGatewayBecomesDependencyFailure() {
        MatchingGateway gateway = gatewayThrowing(feignException(502));

        assertThatThrownBy(() -> gateway.proposeDriver(TRIP_ID, PICKUP, List.of()))
                .isInstanceOf(DependencyFailedException.class)
                .hasMessageContaining("matching-service");
    }

    @Test
    @DisplayName("an unreachable Matching (no Eureka instance) is also a dependency failure")
    void unreachableServiceBecomesDependencyFailure() {
        MatchingGateway gateway = gatewayThrowing(
                new IllegalStateException("No instances available for matching-service"));

        assertThatThrownBy(() -> gateway.proposeDriver(TRIP_ID, PICKUP, List.of()))
                .isInstanceOf(DependencyFailedException.class);
    }

    private MatchingGateway gatewayReturning(Supplier<DriverCandidateDto> answer) {
        return new MatchingGateway(request -> answer.get(), properties);
    }

    private MatchingGateway gatewayThrowing(RuntimeException failure) {
        return new MatchingGateway(new MatchingClient() {
            @Override
            public DriverCandidateDto match(MatchRequestDto request) {
                throw failure;
            }
        }, properties);
    }

    private static DriverCandidateDto driver(String id) {
        return new DriverCandidateDto(id, new LocationDto(37.78, -122.41), 120);
    }

    private static FeignException feignException(int status) {
        Request request = Request.create(
                Request.HttpMethod.POST, "/matches", Map.of(), new byte[0],
                StandardCharsets.UTF_8, new RequestTemplate());
        return FeignException.errorStatus("MatchingClient#match(MatchRequestDto)",
                feign.Response.builder()
                        .status(status)
                        .reason("stubbed")
                        .request(request)
                        .headers(Map.of())
                        .build());
    }
}

