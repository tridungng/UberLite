package com.uberlite.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TimeEstimateDto {
    private final long seconds;

    @JsonCreator
    public TimeEstimateDto(@JsonProperty("seconds") long seconds) {
        this.seconds = seconds;
    }

    public long getSeconds() { return seconds; }
}
