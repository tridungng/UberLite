package com.uberlite.tripservice.repository;

import com.uberlite.common.events.TripState;
import com.uberlite.tripservice.repository.entity.TripEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TripRepository extends JpaRepository<TripEntity, UUID> {

    /**
     * Backs the {@code riderTripCount} pricing input. Counted in the database rather than loaded
     * and filtered in memory, since a long-standing rider may have thousands of trips.
     */
    long countByRiderIdAndStateIn(String riderId, Collection<TripState> states);

    /**
     * The same figure as {@link #countByRiderIdAndStateIn} for every rider at once, aggregated in
     * the database. Backs Discounts Analytics' nightly batch, which needs the whole population and
     * would otherwise have to issue one HTTP call per rider.
     */
    @Query("""
            select t.riderId as riderId, count(t) as tripCount
            from TripEntity t
            where t.state in :states
            group by t.riderId
            order by t.riderId
            """)
    List<RiderTripCountProjection> countTripsPerRider(@Param("states") Collection<TripState> states);

    /** Row shape of {@link #countTripsPerRider}. */
    interface RiderTripCountProjection {
        String getRiderId();

        long getTripCount();
    }
}
