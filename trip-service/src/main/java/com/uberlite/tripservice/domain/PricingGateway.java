package com.uberlite.tripservice.domain;

import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.dto.PriceEstimateRequestDto;
import com.uberlite.common.dto.PriceQuoteDto;
import com.uberlite.tripservice.client.PriceEstimationClient;
import org.springframework.stereotype.Component;

/**
 * Trip Service's view of the Price Estimation Service: give it a trip, get a quote or a
 * {@link DependencyFailedException}. Never a guessed price — a wrong fare is worse than no fare.
 */
@Component
public class PricingGateway {

    static final String DEPENDENCY = "price-estimation-service";

    private final PriceEstimationClient client;

    public PricingGateway(PriceEstimationClient client) {
        this.client = client;
    }

    /**
     * @param riderTripCount completed trips by this rider, which drives first-rides promotions in
     *                       the Discounts Service. Trip Service is the only holder of this figure.
     */
    public PriceQuoteDto quote(String riderId, int riderTripCount, LocationDto pickup, LocationDto dropoff) {
        PriceEstimateRequestDto request =
                new PriceEstimateRequestDto(riderId, riderTripCount, pickup, dropoff);
        return RemoteCalls.call(DEPENDENCY, "quoting a trip for rider " + riderId,
                () -> client.estimate(request));
    }
}

