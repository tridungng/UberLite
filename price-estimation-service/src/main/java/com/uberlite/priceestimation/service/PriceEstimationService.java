package com.uberlite.priceestimation.service;

import com.uberlite.common.geo.H3Util;
import com.uberlite.common.dto.RouteDto;
import com.uberlite.priceestimation.api.PriceEstimateRequest;
import com.uberlite.priceestimation.api.PriceQuoteDto;
import com.uberlite.priceestimation.client.*;
import com.uberlite.priceestimation.domain.PricingCalculator;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PriceEstimationService {
    private final RouteServiceClient routeClient;
    private final TimeEstimationClient timeClient;
    private final SurgePricingClient surgeClient;
    private final TaxTollsClient taxClient;
    private final DiscountsClient discountsClient;
    private final PricingCalculator pricingCalculator;

    public PriceEstimationService(
            RouteServiceClient routeClient,
            TimeEstimationClient timeClient,
            SurgePricingClient surgeClient,
            TaxTollsClient taxClient,
            DiscountsClient discountsClient,
            PricingCalculator pricingCalculator) {
        this.routeClient = routeClient;
        this.timeClient = timeClient;
        this.surgeClient = surgeClient;
        this.taxClient = taxClient;
        this.discountsClient = discountsClient;
        this.pricingCalculator = pricingCalculator;
    }

    public PriceQuoteDto estimatePrice(PriceEstimateRequest req) {
        try {
            double lat1 = req.pickup.lat;
            double lon1 = req.pickup.lon;
            double lat2 = req.dropoff.lat;
            double lon2 = req.dropoff.lon;

            // 1. Route
            RouteServiceClient.RouteEstimate route = null;
            try {
                route = routeClient.estimate(lat1, lon1, lat2, lon2);
            } catch (FeignException fe) {
                throw new DependencyFailedException("route-service failed: " + fe.getMessage());
            }
            double distanceKm = route.straightDistanceKm * route.detourFactor;

            // 2. Time estimation
            String pickupCell = H3Util.latLngToCell(lat1, lon1);
            TimeEstimationClient.TimeEstimate time = null;
            try {
                time = timeClient.estimate(pickupCell, distanceKm);
            } catch (FeignException fe) {
                throw new DependencyFailedException("time-estimation-service failed: " + fe.getMessage());
            }
            double estimatedMinutes = time.estimatedMinutes;

            // 3. Surge
            SurgePricingClient.SurgeResponse surge = null;
            try {
                surge = surgeClient.getMultiplier(pickupCell);
            } catch (FeignException fe) {
                throw new DependencyFailedException("surge-pricing-service failed: " + fe.getMessage());
            }
            double surgeMultiplier = surge.multiplier;

            // 4. Tax & Tolls
            Map<String, Object> taxResp = null;
            try {
                taxResp = taxClient.getTax(pickupCell);
            } catch (FeignException fe) {
                throw new DependencyFailedException("tax-tolls-service failed (tax lookup): " + fe.getMessage());
            }
            double taxRate = ((Number) taxResp.getOrDefault("rate", 0.0)).doubleValue();
            RouteDto tollReq = new RouteDto((long) Math.round(distanceKm * 1000.0), java.util.List.of());
            Map<String, Object> tollResp = null;
            try {
                tollResp = taxClient.estimateToll(tollReq);
            } catch (FeignException fe) {
                throw new DependencyFailedException("tax-tolls-service failed (toll estimate): " + fe.getMessage());
            }
            double tollAmount = ((Number) tollResp.getOrDefault("amount", 0.0)).doubleValue();

            // 5. Discounts
            Map<String, Object> discReq = Map.of("riderId", req.riderId, "riderTripCount", req.riderTripCount);
            Map<String, Object> discResp = null;
            try {
                discResp = discountsClient.evaluate(discReq);
            } catch (FeignException fe) {
                throw new DependencyFailedException("discounts-promotions-service failed: " + fe.getMessage());
            }
            double discountPct = ((Number) discResp.getOrDefault("discountPct", 0.0)).doubleValue();

            // 6. Apply pricing formula
            Map<String, Object> breakdown = new LinkedHashMap<>();
            breakdown.put("distanceKm", distanceKm);
            breakdown.put("estimatedMinutes", estimatedMinutes);
            breakdown.put("surgeMultiplier", surgeMultiplier);
            breakdown.put("tollAmount", tollAmount);
            breakdown.put("discountPct", discountPct);
            breakdown.put("taxRate", taxRate);

            double amount = pricingCalculator.calculateUsingFormula(
                    distanceKm, estimatedMinutes, surgeMultiplier, tollAmount, discountPct, taxRate);
            return new PriceQuoteDto(amount, breakdown);
        } catch (FeignException fe) {
            String url = fe.request() != null ? fe.request().url() : "unknown";
            throw new DependencyFailedException("Downstream service failure: " + url);
        } catch (DependencyFailedException dfe) {
            throw dfe;
        } catch (Exception e) {
            throw new DependencyFailedException("Failed to estimate price: " + e.getMessage());
        }
    }
}
