package com.uberlite.matchinganalytics.domain;

import com.uberlite.common.events.TripEvent;
import com.uberlite.common.events.TripEventPayloadKeys;
import com.uberlite.matchinganalytics.api.dto.MatchLogEntryDto;
import com.uberlite.matchinganalytics.repository.MatchLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns matching-related trip transitions into {@code match_log} rows (ARCHITECTURE.md Sec. 2 —
 * "logging only, no training loop").
 *
 * <p>The value of this table is that it is the only place where a driver's proposal and their
 * answer sit side by side: Trip Service overwrites {@code driver_id} on each retry, so its own row
 * cannot tell you that three drivers declined before the fourth accepted. That history is what a
 * future matching model would train on, which is why the log records every proposal rather than
 * only the successful one.
 */
@Service
public class MatchingEventLogger {

    private static final Logger log = LoggerFactory.getLogger(MatchingEventLogger.class);

    private final MatchLogRepository matchLog;

    public MatchingEventLogger(MatchLogRepository matchLog) {
        this.matchLog = matchLog;
    }

    /**
     * @return whether a row was written; {@code false} for a non-matching transition, an
     *     unattributable event, or a redelivery
     */
    @Transactional
    public boolean record(TripEvent event) {
        Optional<MatchOutcome> outcome = MatchOutcome.fromState(event.getToState());
        if (outcome.isEmpty()) {
            return false;
        }

        String driverId = driverId(event);
        if (driverId == null) {
            // Every matching transition carries a driver id by contract (TripEventPayloadKeys), so
            // this is a producer bug. Log and drop rather than throw: retrying cannot conjure the
            // driver, and a permanently failing record would block the partition for every later
            // event on it.
            log.warn("Dropping {} event for trip {} — no {} in payload",
                    event.getToState(), event.getTripId(), TripEventPayloadKeys.DRIVER_ID);
            return false;
        }

        int written = matchLog.appendIfAbsent(
                event.getTripId(), driverId, outcome.get().name(), event.getTimestamp());
        if (written == 0) {
            log.debug("Match event for trip {} already logged, ignoring redelivery", event.getTripId());
        }
        return written > 0;
    }

    @Transactional(readOnly = true)
    public List<MatchLogEntryDto> findByTripId(String tripId) {
        return matchLog.findByTripIdOrderByOccurredAtAsc(tripId).stream()
                .map(row -> new MatchLogEntryDto(
                        row.getTripId(), row.getDriverId(), row.getOutcome(), row.getOccurredAt()))
                .toList();
    }

    private static String driverId(TripEvent event) {
        Map<String, Object> payload = event.getPayload();
        if (payload == null) {
            return null;
        }
        Object driverId = payload.get(TripEventPayloadKeys.DRIVER_ID);
        return driverId instanceof String s && !s.isBlank() ? s : null;
    }
}
