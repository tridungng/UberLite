package com.uberlite.taxtolls.api;

import com.uberlite.common.dto.RouteDto;
import com.uberlite.taxtolls.domain.TaxTollInfo;
import com.uberlite.taxtolls.domain.TaxTollLookup;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

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

    // POST /tolls/estimate — body: RouteDto
    @PostMapping("/tolls/estimate")
    public ResponseEntity<Map<String, Object>> estimateToll(@RequestBody RouteDto route) {
        double amount = taxTollLookup.estimateToll(route);
        return ResponseEntity.ok(Map.of("amount", amount));
    }
}
