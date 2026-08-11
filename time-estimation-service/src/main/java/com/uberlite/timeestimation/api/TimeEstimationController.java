package com.uberlite.timeestimation.api;

import com.uberlite.common.dto.TimeEstimateDto;
import com.uberlite.timeestimation.domain.HeatmapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the Time Estimation Service.
 *
 * <p>Contract: {@code GET /time/estimate?lat={lat}&lon={lon}} returning {@link TimeEstimateDto}
 * (wire field {@code minutes}). The response type is the shared DTO from {@code common} — the same
 * class price-estimation-service deserializes into — so producer and consumer cannot drift apart.
 */
@RestController
public class TimeEstimationController {

    private final HeatmapService heatmapService;

    public TimeEstimationController(HeatmapService heatmapService) {
        this.heatmapService = heatmapService;
    }

    @GetMapping("/time/estimate")
    public TimeEstimateDto estimate(@RequestParam double lat, @RequestParam double lon) {
        return new TimeEstimateDto(heatmapService.estimateMinutes(lat, lon));
    }
}
