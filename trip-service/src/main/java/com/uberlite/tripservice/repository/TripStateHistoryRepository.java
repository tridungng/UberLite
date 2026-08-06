package com.uberlite.tripservice.repository;

import com.uberlite.tripservice.repository.entity.TripStateHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripStateHistoryRepository extends JpaRepository<TripStateHistoryEntity, UUID> {
    List<TripStateHistoryEntity> findByTripIdOrderByOccurredAtAscIdAsc(UUID tripId);
}
