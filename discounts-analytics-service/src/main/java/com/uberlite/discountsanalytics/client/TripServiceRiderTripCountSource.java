package com.uberlite.discountsanalytics.client;

import com.uberlite.common.dto.RiderTripCountDto;
import com.uberlite.discountsanalytics.domain.RiderTripCountSource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The MVP {@link RiderTripCountSource}: ask Trip Service, which owns trip state.
 *
 * <p>ARCHITECTURE.md Sec. 5 forbids sharing a database between services, so the batch cannot read
 * the trips table directly even though that would be cheaper.
 */
@Component
public class TripServiceRiderTripCountSource implements RiderTripCountSource {

    private final TripServiceClient tripService;

    public TripServiceRiderTripCountSource(TripServiceClient tripService) {
        this.tripService = tripService;
    }

    @Override
    public List<RiderTripCountDto> completedTripCounts() {
        return tripService.riderTripCounts();
    }
}

