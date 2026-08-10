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
import com.uberlite.common.geo.H3Util;
import com.uberlite.priceestimation.client.DiscountsClient;
import com.uberlite.priceestimation.client.RouteServiceClient;
import com.uberlite.priceestimation.client.SurgePricingClient;
import com.uberlite.priceestimation.client.TaxTollsClient;
import com.uberlite.priceestimation.client.TimeEstimationClient;
import com.uberlite.priceestimation.config.PricingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Price Estimation Service (paper Sec. 4.1, "PES"). Pure aggregator: it owns no datastore, it
 * fans out to the five pricing inputs and applies the formula from ARCHITECTURE.md Sec. 8.
 *
 * <p>Every downstream call is mandatory. If any of them fails we surface a 502 naming the culprit
 * rather than defaulting a value — a silently wrong price is worse than a failed quote.
 */
@Service
public class PriceEstimationService {

    private static final Logger log = LoggerFactory.getLogger(PriceEstimationService.class);

    static final String ROUTE_SERVICE = "route-service";
    static final String TIME_ESTIMATION_SERVICE = "time-estimation-service";
    static final String SURGE_PRICING_SERVICE = "surge-pricing-service";
    static final String TAX_TOLLS_SERVICE = "tax-tolls-service";
    static final String DISCOUNTS_SERVICE = "discounts-promotions-service";

    private final RouteServiceClient routeClient;
    private final TimeEstimationClient timeClient;
    private final SurgePricingClient surgeClient;
    private final TaxTollsClient taxTollsClient;
    private final DiscountsClient discountsClient;
    private final PricingCalculator pricingCalculator;
    private final DependencyInvoker dependencies;
    private final PricingProperties properties;

    public PriceEstimationService(
            RouteServiceClient routeClient,
            TimeEstimationClient timeClient,
            SurgePricingClient surgeClient,
            TaxTollsClient taxTollsClient,
            DiscountsClient discountsClient,
            PricingCalculator pricingCalculator,
            DependencyInvoker dependencies,
            PricingProperties properties) {
        this.routeClient = routeClient;
        this.timeClient = timeClient;
        this.surgeClient = surgeClient;
        this.taxTollsClient = taxTollsClient;
        this.discountsClient = discountsClient;
        this.pricingCalculator = pricingCalculator;
        this.dependencies = dependencies;
        this.properties = properties;
    }

    public PriceQuoteDto estimatePrice(PriceEstimateRequestDto request) {
        LocationDto pickup = request.getPickup();
        LocationDto dropoff = request.getDropoff();
        String pickupCell = H3Util.latLngToCell(pickup.getLat(), pickup.getLon());

        double distanceKm = fetchDistanceKm(pickup, dropoff);
        double estimatedMinutes = fetchEstimatedMinutes(pickup);
        double surgeMultiplier = fetchSurgeMultiplier(pickupCell);
        double taxRate = fetchTaxRate();
        double tollAmount = fetchTollAmount(distanceKm, pickup, dropoff);
        double discountPct = fetchDiscountPct(request);

        PriceBreakdown breakdown = pricingCalculator.calculate(new PricingInputs(
                distanceKm, estimatedMinutes, surgeMultiplier, tollAmount, discountPct, taxRate));

        Map<String, Object> breakdownMap = breakdown.toMap();
        breakdownMap.put("pickupH3Cell", pickupCell);
        breakdownMap.put("regionId", properties.getRegionId());

        log.info(
                "Quoted {} {} for rider {} (distanceKm={}, minutes={}, surge={}, tolls={}, discount={}, tax={})",
                breakdown.total(),
                properties.getCurrency(),
                request.getRiderId(),
                distanceKm,
                estimatedMinutes,
                surgeMultiplier,
                tollAmount,
                discountPct,
                taxRate);

        return new PriceQuoteDto(
                breakdown.total().doubleValue(), properties.getCurrency(), breakdownMap);
    }

    private double fetchDistanceKm(LocationDto pickup, LocationDto dropoff) {
        RouteEstimateDto route = dependencies.call(ROUTE_SERVICE, "route estimate", () ->
                routeClient.estimate(pickup.getLat(), pickup.getLon(), dropoff.getLat(), dropoff.getLon()));

        // Route Service only computes a detour factor when the caller already knows the driven
        // distance, which we cannot at quote time. Treating the resulting null as 0 (the previous
        // behaviour, via an unboxed double field) collapsed the trip distance to zero and quoted a
        // near-free ride. Fall back to the configured factor instead.
        Double detourFactor = route.getDetourFactor();
        if (detourFactor == null) {
            detourFactor = properties.getDefaultDetourFactor();
        } else if (detourFactor < 1.0) {
            throw new DependencyFailedException(
                    ROUTE_SERVICE, "returned an implausible detourFactor of " + detourFactor, null);
        }
        return route.getStraightDistanceKm() * detourFactor;
    }

    private double fetchEstimatedMinutes(LocationDto pickup) {
        TimeEstimateDto time = dependencies.call(TIME_ESTIMATION_SERVICE, "travel time estimate", () ->
                timeClient.estimate(pickup.getLat(), pickup.getLon()));
        return require(TIME_ESTIMATION_SERVICE, "minutes", time.getMinutes(), 0.0, Double.MAX_VALUE);
    }

    private double fetchSurgeMultiplier(String pickupCell) {
        SurgeMultiplierDto surge = dependencies.call(SURGE_PRICING_SERVICE, "surge multiplier", () ->
                surgeClient.getMultiplier(pickupCell));
        // ARCHITECTURE.md Sec. 2 clamps the multiplier to [1.0, 3.0]. Anything outside that is a
        // downstream defect, not a rider error, so it is reported as a dependency failure (502).
        return require(SURGE_PRICING_SERVICE, "multiplier", surge.getMultiplier(), 1.0, 3.0);
    }

    private double fetchTaxRate() {
        TaxRateDto tax = dependencies.call(TAX_TOLLS_SERVICE, "tax rate", () ->
                taxTollsClient.getTaxRate(properties.getRegionId()));
        return require(TAX_TOLLS_SERVICE, "tax rate", tax.getRate(), 0.0, 1.0);
    }

    private double fetchTollAmount(double distanceKm, LocationDto pickup, LocationDto dropoff) {
        // Tax & Tolls prices a RouteDto; send the endpoints as well as the distance so a future
        // real toll-segment lookup has the geometry it needs without another contract change.
        RouteDto route = new RouteDto(Math.round(distanceKm * 1000.0), List.of(pickup, dropoff));
        TollEstimateDto toll = dependencies.call(TAX_TOLLS_SERVICE, "toll estimate", () ->
                taxTollsClient.estimateToll(route));
        return require(TAX_TOLLS_SERVICE, "toll amount", toll.getAmount(), 0.0, Double.MAX_VALUE);
    }

    private double fetchDiscountPct(PriceEstimateRequestDto request) {
        DiscountEvaluationRequestDto discountRequest =
                new DiscountEvaluationRequestDto(request.getRiderId(), request.getRiderTripCount());
        DiscountQuoteDto discount = dependencies.call(DISCOUNTS_SERVICE, "discount percentage", () ->
                discountsClient.evaluate(discountRequest));
        return require(DISCOUNTS_SERVICE, "discountPct", discount.getDiscountPct(), 0.0, 1.0);
    }

    /**
     * Range-checks a value supplied by a downstream service. A value outside its contract is the
     * dependency's fault, so it becomes a 502 naming that dependency rather than a 400 or a
     * nonsensical quote.
     */
    private static double require(String dependency, String field, double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            throw new DependencyFailedException(
                    dependency,
                    "returned " + field + "=" + value + ", outside the expected range [" + min + ", " + max + "]",
                    null);
        }
        return value;
    }
}


