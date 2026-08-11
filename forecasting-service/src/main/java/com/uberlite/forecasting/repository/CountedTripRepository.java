package com.uberlite.forecasting.repository;

import com.uberlite.forecasting.repository.entity.CountedTripEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface CountedTripRepository extends JpaRepository<CountedTripEntity, String> {

    /**
     * Claims a trip for counting.
     *
     * @return {@code 1} if this call won the claim and the caller should increment the bucket,
     *     {@code 0} if the trip was already counted. Expressed as a conditional insert rather than
     *     {@code existsById} + {@code save} so the check and the claim are one atomic statement —
     *     the two-statement version has a window where two consumers both see "absent".
     */
    @Modifying
    @Query(value = """
            INSERT INTO counted_trips (trip_id, counted_at)
            VALUES (:tripId, :countedAt)
            ON CONFLICT (trip_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("tripId") String tripId, @Param("countedAt") Instant countedAt);
}

