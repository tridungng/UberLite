package com.uberlite.taxtolls.api;

import com.uberlite.taxtolls.domain.TaxTollInfo;
import com.uberlite.taxtolls.domain.TaxTollLookup;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaxTollsController {
    private final TaxTollLookup taxTollLookup;

    public TaxTollsController(TaxTollLookup taxTollLookup) {
        this.taxTollLookup = taxTollLookup;
    }

    @GetMapping("/tax-tolls/lookup")
    public ResponseEntity<TaxTollInfo> lookup(@RequestParam String region) {
        TaxTollInfo info = taxTollLookup.lookupByRegion(region);
        return ResponseEntity.ok(info);
    }
}
