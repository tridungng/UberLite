package com.uberlite.timeestimation.api;

import com.uberlite.timeestimation.service.HeatmapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TimeEstimationController {
    private final HeatmapService heatmapService = new HeatmapService();

    @GetMapping("/time/estimate")
    public ResponseEntity<?> estimate(@RequestParam double lat, @RequestParam double lon) {
        double minutes = heatmapService.estimateMinutes(lat, lon);
        return ResponseEntity.ok(new java.util.HashMap<String,Object>(){{ put("minutes", minutes); }});
    }
}
