package com.uberlite.tripservice.domain;

import com.uberlite.common.events.TripState;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class TripStateMachine {
    private static final Map<TripState, Set<TripState>> TRANSITIONS = new EnumMap<>(TripState.class);

    static {
        TRANSITIONS.put(TripState.REQUESTED, EnumSet.of(TripState.PRICED));
        TRANSITIONS.put(TripState.PRICED, EnumSet.of(TripState.ACCEPTED_BY_RIDER));
        TRANSITIONS.put(TripState.ACCEPTED_BY_RIDER, EnumSet.of(TripState.CANCELLED_BY_RIDER, TripState.DRIVER_PROPOSED));
        TRANSITIONS.put(TripState.DRIVER_PROPOSED, EnumSet.of(TripState.DRIVER_ACCEPTED, TripState.DRIVER_DECLINED));
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
        if (fromState == TripState.DRIVER_DECLINED) {
            return attemptCount >= 3
                    ? EnumSet.of(TripState.UNMATCHED)
                    : EnumSet.of(TripState.DRIVER_PROPOSED);
        }
        return TRANSITIONS.getOrDefault(fromState, EnumSet.noneOf(TripState.class));
    }

    public void validateTransition(TripState fromState, TripState toState, int attemptCount) {
        Set<TripState> allowed = allowedNextStates(fromState, attemptCount);
        if (!allowed.contains(toState)) {
            throw new IllegalTransitionException(fromState, toState, allowed);
        }
    }
}
