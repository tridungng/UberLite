package com.uberlite.discountspromotions.repository;

import com.uberlite.discountspromotions.repository.entity.PromoRuleEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PromoRuleRepository extends JpaRepository<PromoRuleEntity, String> {
}
