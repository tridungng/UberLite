package com.uberlite.tripservice.domain;

import com.uberlite.common.events.TripState;

import java.util.Set;

public class IllegalTransitionException extends RuntimeException {
    public IllegalTransitionException(TripState fromState, TripState toState, Set<TripState> allowedNextStates) {
        super("Illegal transition from " + fromState + " to " + toState + ". Allowed next states: " + allowedNextStates);
    }
}
