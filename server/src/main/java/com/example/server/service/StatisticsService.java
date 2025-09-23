package com.example.server.service;

import com.example.server.records.StatisticsResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class StatisticsService {

    @Tool(description = "Calculate comprehensive statistics for a dataset of numerical values")
    public StatisticsResult calculateStatistics(
            @ToolParam(description = "JSON array of numerical values") List<Double> values,
            ToolContext toolContext) {

        log.info("Statistics calculation requested for {} values", values.size());

        try {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("No values provided");
            }

            List<Double> sortedValues = values.stream().sorted().collect(Collectors.toList());

            double mean = calculateMean(sortedValues);
            double median = calculateMedian(sortedValues);
            double mode = calculateMode(sortedValues);
            double stdDev = calculateStandardDeviation(sortedValues, mean);
            double variance = stdDev * stdDev;
            double min = sortedValues.get(0);
            double max = sortedValues.get(sortedValues.size() - 1);

            Map<String, Double> percentiles = new HashMap<>();
            percentiles.put("25th", calculatePercentile(sortedValues, 25));
            percentiles.put("50th", calculatePercentile(sortedValues, 50));
            percentiles.put("75th", calculatePercentile(sortedValues, 75));
            percentiles.put("90th", calculatePercentile(sortedValues, 90));
            percentiles.put("95th", calculatePercentile(sortedValues, 95));

            StatisticsResult result =
                    new StatisticsResult(values.size(), Math.round(mean * 100.0) / 100.0,
                            Math.round(median * 100.0) / 100.0, Math.round(mode * 100.0) / 100.0,
                            Math.round(stdDev * 100.0) / 100.0,
                            Math.round(variance * 100.0) / 100.0, min, max, percentiles);

            log.info("Statistics calculated: mean={}, median={}, std_dev={}", result.mean(),
                    result.median(), result.standardDeviation());
            return result;

        } catch (Exception e) {
            log.error("Error calculating statistics", e);
            throw new RuntimeException("Statistics calculation failed: " + e.getMessage());
        }
    }

    @Tool(description = "Calculate grade distribution statistics from letter grades")
    public Map<String, Object> calculateGradeDistribution(
            @ToolParam(description = "JSON array of letter grades (A, B, C, D, F)")
            List<String> letterGrades, ToolContext toolContext) {

        log.info("Grade distribution calculation requested for {} grades", letterGrades.size());

        try {
            if (letterGrades == null || letterGrades.isEmpty()) {
                throw new IllegalArgumentException("No grades provided");
            }

            Map<String, Long> distribution = letterGrades.stream()
                    .collect(Collectors.groupingBy(String::toUpperCase, Collectors.counting()));

            Map<String, Double> percentages = new HashMap<>();
            int total = letterGrades.size();

            for (Map.Entry<String, Long> entry : distribution.entrySet()) {
                double percentage = (entry.getValue().doubleValue() / total) * 100;
                percentages.put(entry.getKey(), Math.round(percentage * 100.0) / 100.0);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("total_grades", total);
            result.put("distribution", distribution);
            result.put("percentages", percentages);

            log.info("Grade distribution calculated for {} grades", total);
            return result;

        } catch (Exception e) {
            log.error("Error calculating grade distribution", e);
            throw new RuntimeException("Grade distribution calculation failed: " + e.getMessage());
        }
    }

    @Tool(description = "Compare two datasets and provide statistical comparison")
    public Map<String, Object> compareDatasets(
            @ToolParam(description = "First dataset - JSON array of numerical values")
            List<Double> dataset1,
            @ToolParam(description = "Second dataset - JSON array of numerical values")
            List<Double> dataset2, ToolContext toolContext) {

        log.info("Dataset comparison requested: {} vs {} values", dataset1.size(), dataset2.size());

        try {
            StatisticsResult stats1 = calculateStatistics(dataset1, toolContext);
            StatisticsResult stats2 = calculateStatistics(dataset2, toolContext);

            Map<String, Object> comparison = new HashMap<>();
            comparison.put("dataset1_stats", stats1);
            comparison.put("dataset2_stats", stats2);

            Map<String, Double> differences = new HashMap<>();
            differences.put("mean_difference", stats2.mean() - stats1.mean());
            differences.put("median_difference", stats2.median() - stats1.median());
            differences.put("stddev_difference",
                    stats2.standardDeviation() - stats1.standardDeviation());

            comparison.put("differences", differences);

            log.info("Dataset comparison completed");
            return comparison;

        } catch (Exception e) {
            log.error("Error comparing datasets", e);
            throw new RuntimeException("Dataset comparison failed: " + e.getMessage());
        }
    }

    private double calculateMean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double calculateMedian(List<Double> sortedValues) {
        int size = sortedValues.size();
        if (size % 2 == 0) {
            return (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2.0;
        } else {
            return sortedValues.get(size / 2);
        }
    }

    private double calculateMode(List<Double> values) {
        Map<Double, Integer> frequency = new HashMap<>();
        for (double value : values) {
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        }

        return frequency.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(0.0);
    }

    private double calculateStandardDeviation(List<Double> values, double mean) {
        double sumSquaredDifferences =
                values.stream().mapToDouble(value -> Math.pow(value - mean, 2)).sum();
        return Math.sqrt(sumSquaredDifferences / values.size());
    }

    private double calculatePercentile(List<Double> sortedValues, int percentile) {
        double index = (percentile / 100.0) * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        } else {
            double lowerValue = sortedValues.get(lowerIndex);
            double upperValue = sortedValues.get(upperIndex);
            return lowerValue + (index - lowerIndex) * (upperValue - lowerValue);
        }
    }
}
