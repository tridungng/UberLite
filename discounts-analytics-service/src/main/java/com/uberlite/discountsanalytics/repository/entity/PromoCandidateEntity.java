package com.uberlite.discountsanalytics.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A rider the nightly batch considers eligible for a promotion.
 *
 * <p>Populated only. Nothing reads it in the live pricing path yet — wiring Discounts &amp;
 * Promotions' rule evaluator to it is a follow-up, see {@code PromoFlagger}.
 */
@Entity
@Table(name = "promo_candidates")
public class PromoCandidateEntity {

    @Id
    @Column(name = "rider_id", nullable = false, length = 64)
    private String riderId;

    @Column(name = "flagged_at", nullable = false)
    private Instant flaggedAt;

    protected PromoCandidateEntity() {
    }

    public PromoCandidateEntity(String riderId, Instant flaggedAt) {
        this.riderId = riderId;
        this.flaggedAt = flaggedAt;
    }

    public String getRiderId() {
        return riderId;
    }

    public Instant getFlaggedAt() {
        return flaggedAt;
    }
}

