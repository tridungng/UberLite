package com.uberlite.tripservice.client;

import com.uberlite.common.dto.PriceEstimateRequestDto;
import com.uberlite.common.dto.PriceQuoteDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Price Estimation Service (ARCHITECTURE.md Sec. 4: Trip Service -> Price Estimation Service).
 *
 * <p>Contract: {@code POST /price-estimates} returning {@link PriceQuoteDto}; {@code 502} when one
 * of PES's own five dependencies is unavailable, in which case no quote exists and the trip must
 * stay in {@code REQUESTED}.
 */
@FeignClient(name = "price-estimation-service", contextId = "priceEstimationClient")
public interface PriceEstimationClient {

    @PostMapping("/price-estimates")
    PriceQuoteDto estimate(@RequestBody PriceEstimateRequestDto request);
}

