package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class RouteDto {
    private final long distanceMeters;
    private final List<LocationDto> points;

    @JsonCreator
    public RouteDto(@JsonProperty("distanceMeters") long distanceMeters,
                    @JsonProperty("points") List<LocationDto> points) {
        this.distanceMeters = distanceMeters;
        this.points = points;
    }

    public long getDistanceMeters() { return distanceMeters; }
    public List<LocationDto> getPoints() { return points; }
}
