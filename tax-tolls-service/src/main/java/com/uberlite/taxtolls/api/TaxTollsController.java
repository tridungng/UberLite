package com.uberlite.taxtolls.api;

import com.uberlite.taxtolls.domain.TaxTollInfo;
import com.uberlite.taxtolls.domain.TaxTollLookup;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.LinkedHashMap;

@RestController
public class TaxTollsController {
    private final TaxTollLookup taxTollLookup;

    public TaxTollsController(TaxTollLookup taxTollLookup) {
        this.taxTollLookup = taxTollLookup;
    }

    // GET /tax/{regionId} — returns {regionId, rate}
    @GetMapping("/tax/{regionId}")
    public ResponseEntity<Map<String, Object>> getTax(@PathVariable String regionId) {
        TaxTollInfo info = taxTollLookup.lookupByRegion(regionId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("regionId", info.getRegion());
        resp.put("rate", info.getTaxRate());
        return ResponseEntity.ok(resp);
    }

    // POST /tolls/estimate — body: {distanceKm}
    @PostMapping("/tolls/estimate")
    public ResponseEntity<Map<String, Object>> estimateToll(@RequestBody Map<String, Object> body) {
        double distanceKm = 0.0;
        if (body.get("distanceKm") instanceof Number) {
            distanceKm = ((Number) body.get("distanceKm")).doubleValue();
        }
        double amount = taxTollLookup.estimateTollByDistance(distanceKm);
        return ResponseEntity.ok(Map.of("amount", amount));
    }
}
