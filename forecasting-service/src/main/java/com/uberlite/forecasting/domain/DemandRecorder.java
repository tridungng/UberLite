package com.uberlite.forecasting.domain;

import com.uberlite.common.events.TripEvent;
import com.uberlite.common.events.TripEventPayloadKeys;
import com.uberlite.common.events.TripState;
import com.uberlite.common.geo.H3Util;
import com.uberlite.forecasting.config.ForecastingProperties;
import com.uberlite.forecasting.repository.CountedTripRepository;
import com.uberlite.forecasting.repository.DemandCountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The write half of the forecast: folds a {@code REQUESTED} trip event into its demand bucket.
 *
 * <p>Split from {@link DemandForecaster} because the two have different reasons to change — this
 * one follows the {@code trip-events} schema, the forecaster follows the prediction rule — and
 * different transactional shapes.
 */
@Service
public class DemandRecorder {

    private static final Logger log = LoggerFactory.getLogger(DemandRecorder.class);

    private final DemandCountRepository demandCounts;
    private final CountedTripRepository countedTrips;
    private final ForecastingProperties properties;

    public DemandRecorder(DemandCountRepository demandCounts,
                          CountedTripRepository countedTrips,
                          ForecastingProperties properties) {
        this.demandCounts = demandCounts;
        this.countedTrips = countedTrips;
        this.properties = properties;
    }

    /**
     * Records one unit of demand for the event's pickup cell and hour.
     *
     * <p>The claim and the increment share a transaction: a crash between them would otherwise
     * leave the trip marked as counted while its demand was never added, and the loss would be
     * permanent because the claim suppresses the redelivery that would have fixed it.
     *
     * @return whether the event was counted; {@code false} means it was a duplicate delivery
     */
    @Transactional
    public boolean record(TripEvent event) {
        if (event.getToState() != TripState.REQUESTED) {
            return false;
        }

        String h3Cell = pickupCell(event);
        if (h3Cell == null) {
            // A REQUESTED event with no usable pickup is a producer-side contract break. Dropping it
            // is right: retrying cannot add a location, and failing the listener would park the
            // partition behind a message that can never succeed.
            log.warn("Skipping REQUESTED event for trip {} — no usable {} in payload",
                    event.getTripId(), TripEventPayloadKeys.PICKUP);
            return false;
        }

        if (countedTrips.claim(event.getTripId(), event.getTimestamp()) == 0) {
            log.debug("Trip {} already counted, ignoring redelivery", event.getTripId());
            return false;
        }

        LocalDateTime localRequestedAt = LocalDateTime.ofInstant(event.getTimestamp(), properties.zone());
        demandCounts.incrementBucket(
                h3Cell, localRequestedAt.getHour(), localRequestedAt.toLocalDate());
        return true;
    }

    /**
     * The pickup's H3 cell, derived here rather than read from the event: {@code trip-events}
     * carries raw coordinates, and H3 is a pure function shared through {@code common}
     * (ARCHITECTURE.md Sec. 2) so every service resolves them the same way.
     *
     * @return {@code null} if the payload has no usable pickup coordinates
     */
    private static String pickupCell(TripEvent event) {
        Map<String, Object> payload = event.getPayload();
        if (payload == null) {
            return null;
        }
        if (!(payload.get(TripEventPayloadKeys.PICKUP) instanceof Map<?, ?> pickup)) {
            return null;
        }
        if (!(pickup.get("lat") instanceof Number lat) || !(pickup.get("lon") instanceof Number lon)) {
            return null;
        }
        return H3Util.latLngToCell(lat.doubleValue(), lon.doubleValue());
    }
}

