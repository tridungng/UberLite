package com.uberlite.priceestimation.client;

import com.uberlite.common.dto.DiscountEvaluationRequestDto;
import com.uberlite.common.dto.DiscountQuoteDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Discounts &amp; Promotions Service (ARCHITECTURE.md Sec. 2, "DPS").
 * Contract: {@code POST /discounts/evaluate}.
 */
@FeignClient(name = "discounts-promotions-service", contextId = "discountsClient")
public interface DiscountsClient {

    @PostMapping("/discounts/evaluate")
    DiscountQuoteDto evaluate(@RequestBody DiscountEvaluationRequestDto request);
}
