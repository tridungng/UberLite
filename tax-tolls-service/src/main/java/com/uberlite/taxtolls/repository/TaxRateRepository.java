package com.uberlite.taxtolls.repository;

import com.uberlite.taxtolls.repository.entity.TaxRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRateRepository extends JpaRepository<TaxRateEntity, String> {
}
