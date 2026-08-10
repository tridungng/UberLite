package com.uberlite.priceestimation.domain;

import com.uberlite.common.dto.DiscountEvaluationRequestDto;
import com.uberlite.common.dto.DiscountQuoteDto;
import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.dto.PriceEstimateRequestDto;
import com.uberlite.common.dto.PriceQuoteDto;
import com.uberlite.common.dto.RouteDto;
import com.uberlite.common.dto.RouteEstimateDto;
import com.uberlite.common.dto.SurgeMultiplierDto;
import com.uberlite.common.dto.TaxRateDto;
import com.uberlite.common.dto.TimeEstimateDto;
import com.uberlite.common.dto.TollEstimateDto;
import com.uberlite.priceestimation.client.DiscountsClient;
import com.uberlite.priceestimation.client.RouteServiceClient;
import com.uberlite.priceestimation.client.SurgePricingClient;
import com.uberlite.priceestimation.client.TaxTollsClient;
import com.uberlite.priceestimation.client.TimeEstimationClient;
import com.uberlite.priceestimation.config.PricingProperties;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Aggregation logic with every Feign client mocked — no network, no Spring context. */
@ExtendWith(MockitoExtension.class)
class PriceEstimationServiceTest {

    @Mock RouteServiceClient routeClient;
    @Mock TimeEstimationClient timeClient;
    @Mock SurgePricingClient surgeClient;
    @Mock TaxTollsClient taxTollsClient;
    @Mock DiscountsClient discountsClient;

    private PricingProperties properties;
    private PriceEstimationService service;

    private static final PriceEstimateRequestDto REQUEST = new PriceEstimateRequestDto(
            "rider-1", 0, new LocationDto(37.7749, -122.4194), new LocationDto(37.8044, -122.2712));

    @BeforeEach
    void setUp() {
        properties = new PricingProperties();
        properties.setCostPerKm(1.0);
        properties.setCostPerMinute(0.5);
        properties.setDefaultDetourFactor(1.1);
        properties.setRegionId("us-ca");
        properties.setCurrency("USD");

        service = new PriceEstimationService(
                routeClient,
                timeClient,
                surgeClient,
                taxTollsClient,
                discountsClient,
                new PricingCalculator(properties),
                new DependencyInvoker(),
                properties);

        lenient().when(routeClient.estimate(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteEstimateDto(10.0, 1.1));
        lenient().when(timeClient.estimate(anyDouble(), anyDouble()))
                .thenReturn(new TimeEstimateDto(20.0));
        lenient().when(surgeClient.getMultiplier(anyString()))
                .thenReturn(new SurgeMultiplierDto("cell", 1.5, 0L));
        lenient().when(taxTollsClient.getTaxRate(anyString()))
                .thenReturn(new TaxRateDto("us-ca", 0.08));
        lenient().when(taxTollsClient.estimateToll(any(RouteDto.class)))
                .thenReturn(new TollEstimateDto(2.5));
        lenient().when(discountsClient.evaluate(any(DiscountEvaluationRequestDto.class)))
                .thenReturn(new DiscountQuoteDto(0.2));
    }

    @Test
    @DisplayName("aggregates all five dependencies into the paper's formula")
    void producesExactQuote() {
        // d = 10.0 * 1.1 = 11.0, t = 20 -> ((1.0*11 + 0.5*20) * 1.5 + 2.5) * 0.8 * 1.08 = 29.38
        PriceQuoteDto quote = service.estimatePrice(REQUEST);

        assertThat(quote.getAmount()).isEqualTo(29.38);
        assertThat(quote.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("breakdown carries every intermediate value needed to reproduce the quote")
    void breakdownIsComplete() {
        PriceQuoteDto quote = service.estimatePrice(REQUEST);

        assertThat(quote.getBreakdown())
                .containsEntry("distanceKm", 11.0)
                .containsEntry("estimatedMinutes", 20.0)
                .containsEntry("surgeMultiplier", 1.5)
                .containsEntry("tollAmount", 2.5)
                .containsEntry("discountPct", 0.2)
                .containsEntry("taxRate", 0.08)
                .containsEntry("costPerKm", 1.0)
                .containsEntry("costPerMinute", 0.5)
                .containsEntry("regionId", "us-ca")
                .containsKeys("pickupH3Cell", "baseFare", "surgedFare", "fareWithTolls",
                        "discountAmount", "fareAfterDiscount", "taxAmount", "total");
    }

    @Test
    @DisplayName("tax is looked up by configured region, not by H3 cell")
    void taxIsLookedUpByRegion() {
        service.estimatePrice(REQUEST);

        ArgumentCaptor<String> region = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(taxTollsClient).getTaxRate(region.capture());
        assertThat(region.getValue()).isEqualTo("us-ca");
    }

    @Test
    @DisplayName("falls back to the configured detour factor when Route Service omits one")
    void nullDetourFactorFallsBackToConfiguration() {
        when(routeClient.estimate(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteEstimateDto(10.0, null));

        PriceQuoteDto quote = service.estimatePrice(REQUEST);

        // Regression guard: a null detourFactor used to deserialize to 0.0 and quote a free ride.
        assertThat(quote.getBreakdown()).containsEntry("distanceKm", 11.0);
        assertThat(quote.getAmount()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("a transport failure is a 502 naming the offending dependency")
    void transportFailureNamesDependency() {
        when(timeClient.estimate(anyDouble(), anyDouble())).thenThrow(connectionRefused());

        assertThatThrownBy(() -> service.estimatePrice(REQUEST))
                .isInstanceOf(DependencyFailedException.class)
                .hasMessageContaining("time-estimation-service")
                .extracting(e -> ((DependencyFailedException) e).getDependency())
                .isEqualTo("time-estimation-service");
    }

    @Test
    @DisplayName("an empty downstream body is a dependency failure, not a zero-valued quote")
    void nullBodyIsDependencyFailure() {
        when(discountsClient.evaluate(any(DiscountEvaluationRequestDto.class))).thenReturn(null);

        assertThatThrownBy(() -> service.estimatePrice(REQUEST))
                .isInstanceOf(DependencyFailedException.class)
                .hasMessageContaining("discounts-promotions-service");
    }

    @Test
    @DisplayName("an out-of-contract surge multiplier is attributed to Surge Pricing, not the rider")
    void outOfRangeSurgeIsDependencyFailure() {
        when(surgeClient.getMultiplier(anyString())).thenReturn(new SurgeMultiplierDto("cell", 42.0, 0L));

        assertThatThrownBy(() -> service.estimatePrice(REQUEST))
                .isInstanceOf(DependencyFailedException.class)
                .hasMessageContaining("surge-pricing-service");
    }

    private static RetryableException connectionRefused() {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://time-estimation-service/time/estimate",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                new RequestTemplate());
        return new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, request);
    }
}

