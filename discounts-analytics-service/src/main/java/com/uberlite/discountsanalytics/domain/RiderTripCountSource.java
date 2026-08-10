package com.uberlite.discountsanalytics.domain;

import com.uberlite.common.dto.RiderTripCountDto;

import java.util.List;

/**
 * Where the nightly batch gets its completed-trip counts.
 *
 * <p>An interface rather than a direct Feign call so the threshold rule can be unit-tested against
 * a hand-written list — the acceptance criterion asks for exactly that — and so the MVP's "just
 * call Trip Service" decision can later become a read replica or a materialised view without the
 * batch noticing.
 */
public interface RiderTripCountSource {

    /** @return completed-trip counts for every rider that has at least one. */
    List<RiderTripCountDto> completedTripCounts();
}

