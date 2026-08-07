package com.uberlite.routeservice.api;

import com.uberlite.routeservice.service.RouteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RouteController {
    private final RouteService routeService = new RouteService();

    @GetMapping("/route/estimate")
    public ResponseEntity<?> estimate(@RequestParam double lat1,
                                      @RequestParam double lon1,
                                      @RequestParam double lat2,
                                      @RequestParam double lon2,
                                      @RequestParam(required = false) Double actualDistanceKm) {
        double straight = routeService.haversineKm(lat1, lon1, lat2, lon2);
        Double detour = actualDistanceKm == null ? null : routeService.detourFactor(straight, actualDistanceKm);
        return ResponseEntity.ok(new java.util.HashMap<String,Object>(){{
            put("straightDistanceKm", straight);
            put("detourFactor", detour);
        }});
    }
}
