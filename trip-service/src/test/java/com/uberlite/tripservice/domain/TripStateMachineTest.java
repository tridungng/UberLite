package com.uberlite.tripservice.domain;

import com.uberlite.common.events.TripState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TripStateMachineTest {
    private final TripStateMachine stateMachine = new TripStateMachine();

    @Test
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

        assertEquals("Illegal transition from REQUESTED to DRIVER_PROPOSED. Allowed next states: [PRICED]", ex.getMessage());
    }

    @Test
    void retryThenUnmatchedPathHonorsAttemptCount() {
        stateMachine.validateTransition(TripState.DRIVER_PROPOSED, TripState.DRIVER_DECLINED, 0);
        assertEquals(List.of(TripState.DRIVER_PROPOSED), stateMachine.allowedNextStates(TripState.DRIVER_DECLINED, 1).stream().toList());

        stateMachine.validateTransition(TripState.DRIVER_DECLINED, TripState.DRIVER_PROPOSED, 1);
        stateMachine.validateTransition(TripState.DRIVER_PROPOSED, TripState.DRIVER_DECLINED, 1);
        assertEquals(List.of(TripState.DRIVER_PROPOSED), stateMachine.allowedNextStates(TripState.DRIVER_DECLINED, 2).stream().toList());

        stateMachine.validateTransition(TripState.DRIVER_DECLINED, TripState.DRIVER_PROPOSED, 2);
        stateMachine.validateTransition(TripState.DRIVER_PROPOSED, TripState.DRIVER_DECLINED, 2);
        assertEquals(List.of(TripState.UNMATCHED), stateMachine.allowedNextStates(TripState.DRIVER_DECLINED, 3).stream().toList());

        assertThrows(IllegalTransitionException.class,
                () -> stateMachine.validateTransition(TripState.DRIVER_DECLINED, TripState.DRIVER_PROPOSED, 3));
        stateMachine.validateTransition(TripState.DRIVER_DECLINED, TripState.UNMATCHED, 3);
    }
}
