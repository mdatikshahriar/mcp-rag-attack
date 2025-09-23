package com.example.server.records;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record StatisticsResult(@JsonProperty("count") Integer count,
                               @JsonProperty("mean") Double mean,
                               @JsonProperty("median") Double median,
                               @JsonProperty("mode") Double mode,
                               @JsonProperty("standard_deviation") Double standardDeviation,
                               @JsonProperty("variance") Double variance,
                               @JsonProperty("min") Double min, @JsonProperty("max") Double max,
                               @JsonProperty("percentiles") Map<String, Double> percentiles) {
}
