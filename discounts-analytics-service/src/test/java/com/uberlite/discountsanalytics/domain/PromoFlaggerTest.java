package com.uberlite.discountsanalytics.domain;

import com.uberlite.common.dto.RiderTripCountDto;
import com.uberlite.discountsanalytics.config.PromoBatchProperties;
import com.uberlite.discountsanalytics.repository.PromoCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The threshold rule, against a mocked trip-count source — no Trip Service, no database, no
 * waiting until 02:00.
 */
class PromoFlaggerTest {

    private static final Instant RUN_AT = Instant.parse("2026-08-11T02:00:00Z");

    private RiderTripCountSource tripCounts;
    private PromoCandidateRepository candidates;
    private PromoFlagger flagger;

    @BeforeEach
    void setUp() {
        tripCounts = mock(RiderTripCountSource.class);
        candidates = mock(PromoCandidateRepository.class);
        flagger = new PromoFlagger(
                tripCounts,
                candidates,
                new PromoBatchProperties(3, "0 0 2 * * *"),
                Clock.fixed(RUN_AT, ZoneOffset.UTC));
    }

    @Test
    void flagsRidersStrictlyBelowTheThreshold() {
        List<RiderTripCountDto> counts = List.of(
                new RiderTripCountDto("rider-zero-ish", 1),
                new RiderTripCountDto("rider-two", 2),
                new RiderTripCountDto("rider-three", 3),
                new RiderTripCountDto("rider-many", 42));

        assertThat(flagger.selectCandidates(counts))
                .containsExactly("rider-zero-ish", "rider-two");
    }

    @Test
    void aRiderOnTheThresholdHasAlreadyHadTheirFreeRides() {
        // "<3" means the third ride is the last discounted one; `<=` would hand out a fourth.
        assertThat(flagger.selectCandidates(List.of(new RiderTripCountDto("rider-three", 3))))
                .isEmpty();
    }

    @Test
    void anEmptyMarketplaceFlagsNobodyRatherThanEverybody() {
        assertThat(flagger.selectCandidates(List.of())).isEmpty();
    }

    @Test
    void persistsEachSelectedRiderStampedWithTheRunTime() {
        when(tripCounts.completedTripCounts()).thenReturn(List.of(
                new RiderTripCountDto("rider-a", 0),
                new RiderTripCountDto("rider-b", 5)));

        assertThat(flagger.flagCandidates()).isEqualTo(1);

        verify(candidates).flag("rider-a", RUN_AT);
        verify(candidates, never()).flag(eq("rider-b"), any());
    }

    @Test
    void sweepsRidersWhoWereNotReFlaggedThisRun() {
        when(tripCounts.completedTripCounts())
                .thenReturn(List.of(new RiderTripCountDto("rider-a", 1)));

        flagger.flagCandidates();

        // Anyone still carrying an older timestamp has crossed the threshold since the last run.
        // Without this the promotion would never end.
        ArgumentCaptor<Instant> before = ArgumentCaptor.forClass(Instant.class);
        verify(candidates).deleteFlaggedBefore(before.capture());
        assertThat(before.getValue()).isEqualTo(RUN_AT);
    }

    @Test
    void aFailingTripCountSourceAbortsTheRunAndLeavesCandidatesAlone() {
        when(tripCounts.completedTripCounts()).thenThrow(new IllegalStateException("trip-service down"));

        assertThatThrownBy(() -> flagger.flagCandidates()).isInstanceOf(IllegalStateException.class);

        // Critical: a partial refresh followed by the sweep would revoke promotions from riders who
        // still qualify, purely because a dependency blinked.
        verify(candidates, never()).flag(any(), any());
        verify(candidates, never()).deleteFlaggedBefore(any());
    }

    @Test
    void theScheduledEntryPointSwallowsFailuresSoTheNextNightStillRuns() {
        when(tripCounts.completedTripCounts()).thenThrow(new IllegalStateException("trip-service down"));

        flagger.scheduledRun();

        verify(tripCounts, times(1)).completedTripCounts();
    }

    @Test
    void rejectsAThresholdThatCouldNeverFlagAnyone() {
        assertThatThrownBy(() -> new PromoBatchProperties(0, "0 0 2 * * *"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

