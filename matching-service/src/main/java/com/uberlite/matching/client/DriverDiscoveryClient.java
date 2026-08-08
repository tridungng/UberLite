package com.uberlite.matching.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "driver-discovery-service")
public interface DriverDiscoveryClient {
    @GetMapping("/drivers/nearest")
    DriverInfo findNearest(@RequestParam String h3Cell, @RequestParam int limit);
}

class DriverInfo {
    public String driverId;
    public String name;
    public String status;
}
