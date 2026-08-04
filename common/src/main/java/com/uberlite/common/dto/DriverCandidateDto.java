package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DriverCandidateDto {
    private final String driverId;
    private final LocationDto location;
    private final long etaSeconds;

    @JsonCreator
    public DriverCandidateDto(@JsonProperty("driverId") String driverId,
                              @JsonProperty("location") LocationDto location,
                              @JsonProperty("etaSeconds") long etaSeconds) {
        this.driverId = driverId;
        this.location = location;
        this.etaSeconds = etaSeconds;
    }

    public String getDriverId() { return driverId; }
    public LocationDto getLocation() { return location; }
    public long getEtaSeconds() { return etaSeconds; }
}
