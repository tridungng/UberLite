package com.uberlite.priceestimation.client;

import com.uberlite.common.dto.TimeEstimateDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Time Estimation Service (ARCHITECTURE.md Sec. 2, "TES").
 *
 * <p>Contract: {@code GET /time/estimate?lat={lat}&lon={lon}} returning {@code {"minutes": n}}.
 *
 * <p>CONTRACT NOTE: the issue describes calling TES "using pickup's H3 cell via H3Util", but the
 * deployed TES endpoint accepts raw lat/lon and derives the H3 cell itself from its heat map. We
 * call the real contract rather than a hypothetical one; the H3 cell is still computed locally for
 * the Surge Pricing call and is reported in the quote breakdown.
 */
@FeignClient(name = "time-estimation-service", contextId = "timeEstimationClient")
public interface TimeEstimationClient {

    @GetMapping("/time/estimate")
    TimeEstimateDto estimate(@RequestParam("lat") double lat, @RequestParam("lon") double lon);
}
