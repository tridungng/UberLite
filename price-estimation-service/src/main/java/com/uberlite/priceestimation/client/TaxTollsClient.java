package com.uberlite.priceestimation.client;

import com.uberlite.common.dto.RouteDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

@FeignClient(name = "tax-tolls-service")
public interface TaxTollsClient {
    @GetMapping("/tax/{regionId}")
    Map<String, Object> getTax(@org.springframework.web.bind.annotation.PathVariable("regionId") String regionId);

    @PostMapping("/tolls/estimate")
    Map<String, Object> estimateToll(@org.springframework.web.bind.annotation.RequestBody RouteDto route);
}
