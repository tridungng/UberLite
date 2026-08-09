package com.uberlite.taxtolls.repository;

import com.uberlite.taxtolls.repository.entity.TollSegmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TollSegmentRepository extends JpaRepository<TollSegmentEntity, String> {
}
