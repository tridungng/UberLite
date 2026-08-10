package com.uberlite.discountsanalytics.api.dto;

import java.time.Instant;

/**
 * One row of {@code GET /promo-candidates}. Local to this module: it is a debugging view, and no
 * other service reads it yet.
 */
public record PromoCandidateDto(String riderId, Instant flaggedAt) {
}

