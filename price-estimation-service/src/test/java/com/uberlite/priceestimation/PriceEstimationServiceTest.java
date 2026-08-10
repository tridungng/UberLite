package com.uberlite.priceestimation;

import com.uberlite.common.dto.RouteDto;
import com.uberlite.priceestimation.api.PriceEstimateRequest;
import com.uberlite.priceestimation.client.*;
import com.uberlite.priceestimation.domain.PricingCalculator;
import com.uberlite.priceestimation.service.PriceEstimationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class PriceEstimationServiceTest {
    RouteServiceClient routeClient = Mockito.mock(RouteServiceClient.class);
    TimeEstimationClient timeClient = Mockito.mock(TimeEstimationClient.class);
    SurgePricingClient surgeClient = Mockito.mock(SurgePricingClient.class);
    TaxTollsClient taxClient = Mockito.mock(TaxTollsClient.class);
    DiscountsClient discountsClient = Mockito.mock(DiscountsClient.class);
    PricingCalculator calculator = new PricingCalculator(1.0, 0.5); // cd=1.0, ct=0.5 for test

    PriceEstimationService svc =
            new PriceEstimationService(routeClient, timeClient, surgeClient, taxClient, discountsClient, calculator);

    @BeforeEach
    void setup() {
        RouteServiceClient.RouteEstimate r = new RouteServiceClient.RouteEstimate();
        r.straightDistanceKm = 10.0;
        r.detourFactor = 1.1;
        when(routeClient.estimate(any(Double.class), any(Double.class), any(Double.class), any(Double.class)))
                .thenReturn(r);

        TimeEstimationClient.TimeEstimate t = new TimeEstimationClient.TimeEstimate();
        t.estimatedMinutes = 20.0;
        when(timeClient.estimate(any(String.class), any(Double.class))).thenReturn(t);

        SurgePricingClient.SurgeResponse s = new SurgePricingClient.SurgeResponse();
        s.multiplier = 1.5;
        when(surgeClient.getMultiplier(any(String.class))).thenReturn(s);

        when(taxClient.getTax(any(String.class))).thenReturn(Map.of("rate", 0.08));
        when(taxClient.estimateToll(any(RouteDto.class))).thenReturn(Map.of("amount", 2.5));

        when(discountsClient.evaluate(any(Map.class))).thenReturn(Map.of("discountPct", 0.2));
    }

    @Test
    void formula_calculation_matches_expected() {
        PriceEstimateRequest req = new PriceEstimateRequest();
        req.riderId = "r1";
        req.riderTripCount = 0;
        req.pickup = new PriceEstimateRequest.LocationDto();
        req.pickup.lat = 37.0;
        req.pickup.lon = -122.0;
        req.dropoff = new PriceEstimateRequest.LocationDto();
        req.dropoff.lat = 37.5;
        req.dropoff.lon = -122.5;

        var quote = svc.estimatePrice(req);
        assertThat(quote).isNotNull();
        assertThat(quote.breakdown)
                .containsKeys(
                        "distanceKm", "estimatedMinutes", "surgeMultiplier", "tollAmount", "discountPct", "taxRate");
        // With cd=1.0, ct=0.5: distance = 11.0 km -> cd*d=11.0; ct*t=10.0 => base=21.0
        // after surge (1.5) => 31.5; + toll 2.5 => 34.0; after discount 20% => 27.2; after tax 8% => 29.376 -> rounded
        // 29.38
        assertThat(quote.amount).isEqualTo(29.38);
    }
}
