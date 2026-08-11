package com.uberlite.discountsanalytics.repository;

import com.uberlite.discountsanalytics.repository.entity.PromoCandidateEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PromoCandidateRepository extends JpaRepository<PromoCandidateEntity, String> {

    List<PromoCandidateEntity> findAllByOrderByRiderIdAsc();

    /**
     * Flags a rider, refreshing {@code flagged_at} if they were already flagged.
     *
     * <p>An upsert rather than an insert because the batch is idempotent by design: a retried or
     * twice-scheduled run must leave the same set of candidates, not fail on the primary key.
     */
    @Modifying
    @Query(value = """
            INSERT INTO promo_candidates (rider_id, flagged_at)
            VALUES (:riderId, :flaggedAt)
            ON CONFLICT (rider_id) DO UPDATE SET flagged_at = EXCLUDED.flagged_at
            """, nativeQuery = true)
    void flag(@Param("riderId") String riderId, @Param("flaggedAt") Instant flaggedAt);

    /**
     * Removes riders who are no longer candidates — typically because they crossed the trip
     * threshold since the last run.
     *
     * <p>Without this the table would only ever grow, and a rider would keep a "new rider" discount
     * forever after their third trip, which is precisely the rule the promotion is meant to end.
     */
    @Modifying
    @Query("delete from PromoCandidateEntity c where c.flaggedAt < :before")
    int deleteFlaggedBefore(@Param("before") Instant before);
}

