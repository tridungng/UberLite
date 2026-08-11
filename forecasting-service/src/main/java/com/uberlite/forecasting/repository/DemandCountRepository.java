package com.uberlite.forecasting.repository;

import com.uberlite.forecasting.repository.entity.DemandCountId;

import com.uberlite.forecasting.repository.entity.DemandCountEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DemandCountRepository extends JpaRepository<DemandCountEntity, DemandCountId> {

    /**
     * Upserts a bucket and adds one, atomically.
     *
     * <p>Deliberately a native {@code ON CONFLICT} rather than "find, increment, save": several
     * consumer threads (or a rebalanced partition replayed on two instances) hitting the same cell
     * and hour would otherwise read the same value and each write {@code n + 1}, silently losing
     * demand — exactly the signal this service exists to measure.
     */
    @Modifying
    @Query(value = """
            INSERT INTO demand_counts (h3_cell, hour_of_day, day_bucket, count)
            VALUES (:h3Cell, :hourOfDay, :dayBucket, 1)
            ON CONFLICT (h3_cell, hour_of_day, day_bucket)
            DO UPDATE SET count = demand_counts.count + 1
            """, nativeQuery = true)
    void incrementBucket(@Param("h3Cell") String h3Cell,
                         @Param("hourOfDay") int hourOfDay,
                         @Param("dayBucket") LocalDate dayBucket);

    /**
     * The buckets inside a forecast window, newest first. Days with no demand have no row; the
     * rolling average treats their absence as a genuine zero rather than as missing data.
     */
    @Query("""
            select d from DemandCountEntity d
            where d.id.h3Cell = :h3Cell
              and d.id.hourOfDay = :hourOfDay
              and d.id.dayBucket between :from and :to
            order by d.id.dayBucket desc
            """)
    List<DemandCountEntity> findWindow(@Param("h3Cell") String h3Cell,
                                       @Param("hourOfDay") short hourOfDay,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);
}

