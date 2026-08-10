package com.uberlite.matchinganalytics.domain;

import com.uberlite.common.events.TripState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter that decides what belongs in {@code match_log}. Pure, so it can be exhaustive over the
 * trip state machine rather than over whichever states happened to be sent in an integration test.
 */
class MatchOutcomeTest {

    @Test
    void mapsTheThreeMatchingTransitions() {
        assertThat(MatchOutcome.fromState(TripState.DRIVER_PROPOSED)).contains(MatchOutcome.PROPOSED);
        assertThat(MatchOutcome.fromState(TripState.DRIVER_ACCEPTED)).contains(MatchOutcome.ACCEPTED);
        assertThat(MatchOutcome.fromState(TripState.DRIVER_DECLINED)).contains(MatchOutcome.DECLINED);
    }

    /**
     * Every other state — including {@code UNMATCHED}, which is a matching <em>outcome</em> but has
     * no driver to attribute it to — must be ignored. Exhaustive so that adding a state to the
     * machine cannot silently start writing rows with a null driver.
     */
    @ParameterizedTest
    @EnumSource(value = TripState.class, names = {"DRIVER_PROPOSED", "DRIVER_ACCEPTED", "DRIVER_DECLINED"},
            mode = EnumSource.Mode.EXCLUDE)
    void ignoresEveryOtherTransition(TripState state) {
        assertThat(MatchOutcome.fromState(state)).isEmpty();
    }

    @Test
    void toleratesAMissingState() {
        assertThat(MatchOutcome.fromState(null)).isEmpty();
    }
}

