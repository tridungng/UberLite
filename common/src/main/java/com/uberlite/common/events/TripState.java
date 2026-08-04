package com.uberlite.common.events;

public enum TripState {
    REQUESTED,
    PRICED,
    ACCEPTED_BY_RIDER,
    CANCELLED_BY_RIDER,
    DRIVER_PROPOSED,
    DRIVER_ACCEPTED,
    DRIVER_DECLINED,
    EN_ROUTE_TO_PICKUP,
    RIDER_PICKED_UP,
    COMPLETED,
    PAID,
    UNMATCHED
}
