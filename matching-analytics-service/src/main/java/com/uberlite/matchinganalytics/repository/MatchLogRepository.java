package com.uberlite.matchinganalytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MatchLogRepository extends JpaRepository<MatchLogEntity, Long> {

    /** Backs {@code GET /match-log/{tripId}}: a trip's matching story, oldest first. */
    List<MatchLogEntity> findByTripIdOrderByOccurredAtAsc(String tripId);

    /**
     * Appends a row unless the identical event has already been logged.
     *
     * <p>A native {@code ON CONFLICT DO NOTHING} rather than "select, then insert if missing":
     * the read-then-write version costs a round trip per event on the hot path and still races two
     * consumers into a constraint violation, which would then surface as a listener error and stall
     * the partition.
     *
     * @return {@code 1} if a row was written, {@code 0} if this was a redelivery
     */
    @Modifying
    @Query(value = """
            INSERT INTO match_log (trip_id, driver_id, outcome, occurred_at)
            VALUES (:tripId, :driverId, :outcome, :occurredAt)
            ON CONFLICT ON CONSTRAINT uq_match_log_event DO NOTHING
            """, nativeQuery = true)
    int appendIfAbsent(@Param("tripId") String tripId,
                       @Param("driverId") String driverId,
                       @Param("outcome") String outcome,
                       @Param("occurredAt") Instant occurredAt);
}

