package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public class LocationDto {

    @DecimalMin(value = "-90.0", message = "lat must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "lat must be between -90 and 90")
    private final double lat;

    @DecimalMin(value = "-180.0", message = "lon must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "lon must be between -180 and 180")
    private final double lon;

    @JsonCreator
    public LocationDto(@JsonProperty("lat") double lat, @JsonProperty("lon") double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public double getLat() { return lat; }
    public double getLon() { return lon; }
}
