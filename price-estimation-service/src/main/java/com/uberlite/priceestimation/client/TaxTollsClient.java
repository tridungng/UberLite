package com.uberlite.priceestimation.client;

import com.uberlite.common.dto.RouteDto;
import com.uberlite.common.dto.TaxRateDto;
import com.uberlite.common.dto.TollEstimateDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Tax &amp; Tolls Service (ARCHITECTURE.md Sec. 2, "TTS").
 * Contracts: {@code GET /tax/{regionId}} and {@code POST /tolls/estimate}.
 */
@FeignClient(name = "tax-tolls-service", contextId = "taxTollsClient")
public interface TaxTollsClient {

    @GetMapping("/tax/{regionId}")
    TaxRateDto getTaxRate(@PathVariable("regionId") String regionId);

    @PostMapping("/tolls/estimate")
    TollEstimateDto estimateToll(@RequestBody RouteDto route);
}
