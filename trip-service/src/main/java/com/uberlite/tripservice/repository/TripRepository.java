package com.uberlite.tripservice.repository;

import com.uberlite.common.events.TripState;
import com.uberlite.tripservice.repository.entity.TripEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface TripRepository extends JpaRepository<TripEntity, UUID> {

    /**
     * Backs the {@code riderTripCount} pricing input. Counted in the database rather than loaded
     * and filtered in memory, since a long-standing rider may have thousands of trips.
     */
    long countByRiderIdAndStateIn(String riderId, Collection<TripState> states);
}
