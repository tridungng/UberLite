package com.uberlite.tripservice.domain;

import com.uberlite.common.events.TripState;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class TripStateMachine {

    /**
     * The retry budget k from ARCHITECTURE.md Sec. 3: after this many declines the trip is parked in
     * {@code UNMATCHED} rather than shopped around forever.
     */
    public static final int MAX_MATCH_ATTEMPTS = 3;

    private static final Map<TripState, Set<TripState>> TRANSITIONS = new EnumMap<>(TripState.class);

    static {
        TRANSITIONS.put(TripState.REQUESTED, EnumSet.of(TripState.PRICED));
        TRANSITIONS.put(TripState.PRICED, EnumSet.of(TripState.ACCEPTED_BY_RIDER));
        // UNMATCHED is reachable straight from ACCEPTED_BY_RIDER: the very first call to Matching
        // can come back 404 (empty marketplace), and there is no driver to decline in that case.
        TRANSITIONS.put(TripState.ACCEPTED_BY_RIDER,
                EnumSet.of(TripState.CANCELLED_BY_RIDER, TripState.DRIVER_PROPOSED, TripState.UNMATCHED));
        TRANSITIONS.put(TripState.DRIVER_PROPOSED, EnumSet.of(TripState.DRIVER_ACCEPTED, TripState.DRIVER_DECLINED));
        // Within budget a decline may be retried, but Matching can still answer 404 on the retry,
        // so UNMATCHED must remain reachable here too.
        TRANSITIONS.put(TripState.DRIVER_DECLINED, EnumSet.of(TripState.DRIVER_PROPOSED, TripState.UNMATCHED));
        TRANSITIONS.put(TripState.DRIVER_ACCEPTED, EnumSet.of(TripState.EN_ROUTE_TO_PICKUP));
        TRANSITIONS.put(TripState.EN_ROUTE_TO_PICKUP, EnumSet.of(TripState.RIDER_PICKED_UP));
        TRANSITIONS.put(TripState.RIDER_PICKED_UP, EnumSet.of(TripState.COMPLETED));
        TRANSITIONS.put(TripState.COMPLETED, EnumSet.of(TripState.PAID));
        TRANSITIONS.put(TripState.CANCELLED_BY_RIDER, EnumSet.noneOf(TripState.class));
        TRANSITIONS.put(TripState.UNMATCHED, EnumSet.noneOf(TripState.class));
        TRANSITIONS.put(TripState.PAID, EnumSet.noneOf(TripState.class));
    }

    public Set<TripState> allowedNextStates(TripState fromState, int attemptCount) {
        if (fromState == TripState.DRIVER_DECLINED && hasExhaustedAttempts(attemptCount)) {
            // Retry budget spent: UNMATCHED is the only way out.
            return EnumSet.of(TripState.UNMATCHED);
        }
        return TRANSITIONS.getOrDefault(fromState, EnumSet.noneOf(TripState.class));
    }

    public void validateTransition(TripState fromState, TripState toState, int attemptCount) {
        Set<TripState> allowed = allowedNextStates(fromState, attemptCount);
        if (!allowed.contains(toState)) {
            throw new IllegalTransitionException(fromState, toState, allowed);
        }
    }

    /** @return true once the k=3 retry budget is spent and no further driver may be proposed. */
    public boolean hasExhaustedAttempts(int attemptCount) {
        return attemptCount >= MAX_MATCH_ATTEMPTS;
    }
}
