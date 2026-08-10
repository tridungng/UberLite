package com.uberlite.common.events;

/**
 * Keys used in the {@code payload} map of a {@link TripEvent} on {@link Topics#TRIP_EVENTS}.
 *
 * <p>The payload is part of the published event schema, so — like the topic name itself — it is
 * defined once here rather than inlined as string literals in Trip Service. Consumers
 * (Matching Analytics, Discounts Analytics) read these keys instead of guessing.
 *
 * <p>Not every key appears on every event; each constant documents which transition emits it.
 */
public final class TripEventPayloadKeys {

    private TripEventPayloadKeys() {
    }

    /** {@code null -> REQUESTED}: the rider who requested the trip. */
    public static final String RIDER_ID = "riderId";

    /** {@code null -> REQUESTED}: {@code {lat, lon}} of the pickup. */
    public static final String PICKUP = "pickup";

    /** {@code null -> REQUESTED}: {@code {lat, lon}} of the dropoff. */
    public static final String DROPOFF = "dropoff";

    /** {@code REQUESTED -> PRICED}: quoted amount, as a number. */
    public static final String QUOTED_PRICE = "quotedPrice";

    /** {@code REQUESTED -> PRICED}: ISO currency code of {@link #QUOTED_PRICE}. */
    public static final String CURRENCY = "currency";

    /** {@code REQUESTED -> PRICED}: the Price Estimation Service breakdown, verbatim. */
    public static final String PRICE_BREAKDOWN = "breakdown";

    /** {@code * -> DRIVER_PROPOSED}: the proposed driver. */
    public static final String DRIVER_ID = "driverId";

    /** {@code * -> DRIVER_PROPOSED}: proposed driver's ETA to the pickup, in seconds. */
    public static final String ETA_SECONDS = "etaSeconds";

    /** {@code DRIVER_PROPOSED -> DRIVER_DECLINED}: 1-based index of the matching attempt. */
    public static final String ATTEMPT = "attempt";

    /** {@code * -> UNMATCHED}: why the trip left the matching pipeline. */
    public static final String REASON = "reason";

    /** {@code * -> UNMATCHED} / {@code -> DRIVER_PROPOSED}: drivers already excluded from matching. */
    public static final String DECLINED_DRIVER_IDS = "declinedDriverIds";
}

