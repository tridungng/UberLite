package com.uberlite.tripservice.domain;

import com.uberlite.common.events.TripState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TripStateMachineTest {

    private final TripStateMachine stateMachine = new TripStateMachine();

    @Test
    @DisplayName("the happy path of ARCHITECTURE.md Sec. 3 is legal end to end")
    void happyPathEndToEndTransitionsAreAllowed() {
        List<TripState> path = List.of(
                TripState.PRICED,
                TripState.ACCEPTED_BY_RIDER,
                TripState.DRIVER_PROPOSED,
                TripState.DRIVER_ACCEPTED,
                TripState.EN_ROUTE_TO_PICKUP,
                TripState.RIDER_PICKED_UP,
                TripState.COMPLETED,
                TripState.PAID
        );

        TripState current = TripState.REQUESTED;
        for (TripState next : path) {
            stateMachine.validateTransition(current, next, 0);
            current = next;
        }
    }

    @Test
    void illegalTransitionIsRejected() {
        IllegalTransitionException ex = assertThrows(IllegalTransitionException.class,
                () -> stateMachine.validateTransition(TripState.REQUESTED, TripState.DRIVER_PROPOSED, 0));

        assertEquals("Illegal transition from REQUESTED to DRIVER_PROPOSED. Allowed next states: [PRICED]",
                ex.getMessage());
    }

    @Test
    @DisplayName("within the k=3 budget a decline may be retried, and UNMATCHED stays reachable")
    void withinBudgetADeclineMayBeRetried() {
        for (int attempt = 1; attempt < TripStateMachine.MAX_MATCH_ATTEMPTS; attempt++) {
            assertThat(stateMachine.allowedNextStates(TripState.DRIVER_DECLINED, attempt))
                    .containsExactlyInAnyOrder(TripState.DRIVER_PROPOSED, TripState.UNMATCHED);
            assertThat(stateMachine.hasExhaustedAttempts(attempt)).isFalse();

            stateMachine.validateTransition(TripState.DRIVER_DECLINED, TripState.DRIVER_PROPOSED, attempt);
            stateMachine.validateTransition(TripState.DRIVER_PROPOSED, TripState.DRIVER_DECLINED, attempt);
        }
    }

    @Test
    @DisplayName("once the k=3 budget is spent, UNMATCHED is the only way out")
    void exhaustedBudgetLeavesOnlyUnmatched() {
        int exhausted = TripStateMachine.MAX_MATCH_ATTEMPTS;

        assertThat(stateMachine.hasExhaustedAttempts(exhausted)).isTrue();
        assertThat(stateMachine.allowedNextStates(TripState.DRIVER_DECLINED, exhausted))
                .containsExactly(TripState.UNMATCHED);

        assertThrows(IllegalTransitionException.class,
                () -> stateMachine.validateTransition(TripState.DRIVER_DECLINED, TripState.DRIVER_PROPOSED, exhausted));
        stateMachine.validateTransition(TripState.DRIVER_DECLINED, TripState.UNMATCHED, exhausted);
    }

    @Test
    @DisplayName("an empty marketplace on the first match makes UNMATCHED reachable from ACCEPTED_BY_RIDER")
    void unmatchedIsReachableOnTheFirstMatchAttempt() {
        stateMachine.validateTransition(TripState.ACCEPTED_BY_RIDER, TripState.UNMATCHED, 0);
    }

    @Test
    void terminalStatesHaveNoSuccessors() {
        for (TripState terminal : List.of(TripState.PAID, TripState.UNMATCHED, TripState.CANCELLED_BY_RIDER)) {
            assertThat(stateMachine.allowedNextStates(terminal, 0)).isEmpty();
        }
    }
}
