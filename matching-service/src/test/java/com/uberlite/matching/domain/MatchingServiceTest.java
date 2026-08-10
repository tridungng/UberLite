package com.uberlite.matching.domain;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.dto.MatchRequestDto;
import com.uberlite.common.dto.RouteEstimateDto;
import com.uberlite.matching.client.DriverDiscoveryClient;
import com.uberlite.matching.client.RouteServiceClient;
import com.uberlite.matching.config.MatchingProperties;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ranking logic with both Feign clients mocked (issue acceptance criterion 1).
 */
@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    private static final LocationDto PICKUP = new LocationDto(37.7749, -122.4194);

    @Mock private DriverDiscoveryClient driverDiscoveryClient;
    @Mock private RouteServiceClient routeServiceClient;

    private MatchingProperties properties;
    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        properties = new MatchingProperties();
        properties.setAverageSpeedMps(10.0);
        properties.setDefaultDetourFactor(1.0);
        properties.setRadiusMeters(3000);
        properties.setCandidateLimit(10);
        matchingService = new MatchingService(driverDiscoveryClient, routeServiceClient, properties);
    }

    private static DriverCandidateDto candidate(String id, double lat) {
        // etaSeconds from DRS is deliberately a large decoy: we must recompute it ourselves.
        return new DriverCandidateDto(id, new LocationDto(lat, -122.4194), 99_999);
    }

    private void stubRoute(double lat, double straightKm) {
        when(routeServiceClient.estimate(eq(lat), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteEstimateDto(straightKm, null));
    }

    /** A realistic Feign failure — what a dead downstream actually throws. */
    private static FeignException feignFailure(String message) {
        return new FeignException.ServiceUnavailable(
                message,
                Request.create(Request.HttpMethod.GET, "/", Map.of(), null, StandardCharsets.UTF_8, null),
                null,
                Map.of());
    }

    @Test
    @DisplayName("three candidates -> the lowest pickup ETA wins")
    void picksClosestCandidateByEta() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(candidate("far", 1.0), candidate("near", 2.0), candidate("mid", 3.0)));
        stubRoute(1.0, 9.0);
        stubRoute(2.0, 1.5);   // closest
        stubRoute(3.0, 4.0);

        DriverCandidateDto best = matchingService.findBestMatch(new MatchRequestDto("trip-1", PICKUP));

        assertThat(best.getDriverId()).isEqualTo("near");
        // 1.5 km / 10 m/s = 150s — our own ETA, not the 99_999 decoy from Driver Discovery.
        assertThat(best.getEtaSeconds()).isEqualTo(150);
    }

    @Test
    @DisplayName("configured radius and limit are passed through to Driver Discovery")
    void honoursConfiguredRadiusAndLimit() {
        properties.setRadiusMeters(1500);
        properties.setCandidateLimit(4);
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(candidate("only", 1.0)));
        stubRoute(1.0, 1.0);

        matchingService.findBestMatch(new MatchRequestDto("trip-1", PICKUP));

        ArgumentCaptor<Double> radius = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(driverDiscoveryClient)
                .nearby(eq(PICKUP.getLat()), eq(PICKUP.getLon()), radius.capture(), limit.capture());
        assertThat(radius.getValue()).isEqualTo(1500.0);
        assertThat(limit.getValue()).isEqualTo(4);
    }

    @Test
    @DisplayName("no drivers nearby -> NoDriversAvailableException (404), and no route calls made")
    void throwsWhenNoDriversAvailable() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> matchingService.findBestMatch(new MatchRequestDto("trip-1", PICKUP)))
                .isInstanceOf(NoDriversAvailableException.class);
        verify(routeServiceClient, never()).estimate(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("one unroutable candidate is skipped, not fatal")
    void skipsCandidateWhoseRouteLookupFails() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(candidate("broken", 1.0), candidate("ok", 2.0)));
        when(routeServiceClient.estimate(eq(1.0), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(feignFailure("route-service blew up"));
        stubRoute(2.0, 5.0);

        DriverCandidateDto best = matchingService.findBestMatch(new MatchRequestDto("trip-1", PICKUP));

        assertThat(best.getDriverId()).isEqualTo("ok");
    }

    @Test
    @DisplayName("candidates exist but none can be scored -> 502, not a misleading 404")
    void throwsDependencyFailureWhenNoCandidateCanBeScored() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(candidate("a", 1.0)));
        when(routeServiceClient.estimate(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(feignFailure("route-service down"));

        assertThatThrownBy(() -> matchingService.findBestMatch(new MatchRequestDto("trip-1", PICKUP)))
                .isInstanceOf(DependencyFailedException.class)
                .extracting(e -> ((DependencyFailedException) e).getDependency())
                .isEqualTo("route-service");
    }

    @Test
    @DisplayName("Driver Discovery outage -> 502, never a 404 (Trip Service must not burn its retries)")
    void throwsDependencyFailureWhenDiscoveryIsDown() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenThrow(feignFailure("connection refused"));

        assertThatThrownBy(() -> matchingService.findBestMatch(new MatchRequestDto("trip-1", PICKUP)))
                .isInstanceOf(DependencyFailedException.class)
                .extracting(e -> ((DependencyFailedException) e).getDependency())
                .isEqualTo("driver-discovery-service");
    }

    @Test
    @DisplayName("a bug in our own code surfaces as itself, not as a fake dependency outage")
    void doesNotDisguiseOurOwnBugsAsDependencyFailures() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new NullPointerException("bug in our mapping code"));

        assertThatThrownBy(() -> matchingService.findBestMatch(new MatchRequestDto("trip-1", PICKUP)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ties are broken deterministically by driverId")
    void breaksTiesDeterministically() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(candidate("zulu", 1.0), candidate("alpha", 2.0)));
        stubRoute(1.0, 3.0);
        stubRoute(2.0, 3.0);

        DriverCandidateDto best = matchingService.findBestMatch(new MatchRequestDto("trip-1", PICKUP));

        assertThat(best.getDriverId()).isEqualTo("alpha");
    }

    @Test
    @DisplayName("a candidate with no location is skipped rather than NPE-ing the request")
    void skipsMalformedCandidate() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(java.util.Arrays.asList(
                        new DriverCandidateDto("ghost", null, 0), candidate("real", 2.0)));
        stubRoute(2.0, 1.0);

        DriverCandidateDto best = matchingService.findBestMatch(new MatchRequestDto("trip-1", PICKUP));

        assertThat(best.getDriverId()).isEqualTo("real");
    }

    @Test
    @DisplayName("an excluded driver is never proposed, even when they are the nearest")
    void excludesPreviousDecliners() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(candidate("decliner", 1.0), candidate("next-best", 2.0)));
        // The decliner is nearest and would win outright if we did not filter.
        stubRoute(2.0, 5.0);

        DriverCandidateDto best = matchingService.findBestMatch(
                new MatchRequestDto("trip-1", PICKUP, List.of("decliner")));

        assertThat(best.getDriverId()).isEqualTo("next-best");
    }

    @Test
    @DisplayName("excluded candidates are dropped before the Route Service fan-out")
    void doesNotPayForRoutingExcludedCandidates() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(candidate("decliner", 1.0), candidate("next-best", 2.0)));
        stubRoute(2.0, 5.0);

        matchingService.findBestMatch(new MatchRequestDto("trip-1", PICKUP, List.of("decliner")));

        // Only the eligible candidate cost us a network call.
        verify(routeServiceClient, never()).estimate(eq(1.0), anyDouble(), anyDouble(), anyDouble());
        verify(routeServiceClient).estimate(eq(2.0), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("every nearby driver already declined -> 404, not a re-proposal")
    void returnsNoDriversWhenEveryCandidateIsExcluded() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(candidate("a", 1.0), candidate("b", 2.0)));

        assertThatThrownBy(() -> matchingService.findBestMatch(
                new MatchRequestDto("trip-1", PICKUP, List.of("a", "b"))))
                .isInstanceOf(NoDriversAvailableException.class);
        verify(routeServiceClient, never()).estimate(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("an absent excludedDriverIds behaves as an empty list")
    void nullExclusionListIsTreatedAsEmpty() {
        when(driverDiscoveryClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(candidate("only", 1.0)));
        stubRoute(1.0, 1.0);

        DriverCandidateDto best = matchingService.findBestMatch(
                new MatchRequestDto("trip-1", PICKUP, null));

        assertThat(best.getDriverId()).isEqualTo("only");
    }
}




