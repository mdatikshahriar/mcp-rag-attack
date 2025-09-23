package com.example.server.service;

import com.example.server.records.Grade;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@AllArgsConstructor
public class GpaCalculatorService {

    @Tool(description = "Calculate GPA (4.0 scale) from a list of grades with course information. Accepts grades with studentId, courseId, grade (letter), and points (numeric)")
    public Map<String, Object> calculateGPA(@ToolParam(
            description = "JSON array of grade objects with studentId, courseId, grade (letter), points (numeric), and optionally credits")
    List<Grade> grades, @ToolParam(
            description = "Optional: JSON array of course objects with courseId and credits if not provided in grades")
    List<Map<String, Object>> courses, ToolContext toolContext) {

        log.info("GPA calculation requested for {} grades", grades.size());

        try {
            if (grades == null || grades.isEmpty()) {
                throw new IllegalArgumentException("No grades provided");
            }

            // Create a map of courseId to credits from courses parameter
            Map<String, Integer> courseCredits = new HashMap<>();
            if (courses != null) {
                for (Map<String, Object> course : courses) {
                    String courseId = (String) course.get("courseId");
                    Integer credits = (Integer) course.get("credits");
                    if (courseId != null && credits != null) {
                        courseCredits.put(courseId, credits);
                    }
                }
            }

            double totalGradePoints = 0.0;
            double totalCredits = 0.0;
            int validGrades = 0;

            for (Grade grade : grades) {
                if (grade.points() == null) {
                    log.warn("Skipping grade with missing grade points for course: {}",
                            grade.courseId());
                    continue;
                }

                // Get credits from grade object or course lookup
                Integer credits = grade.credits();
                if (credits == null) {
                    credits = courseCredits.get(grade.courseId());
                }

                if (credits == null) {
                    log.warn("No credits found for course: {}, using default of 3 credits",
                            grade.courseId());
                    credits = 3; // Default credit value
                }

                double gradePoints = grade.points();

                // Validate grade points are within reasonable range (0-4 for 4.0 scale)
                if (gradePoints < 0 || gradePoints > 4) {
                    log.warn("Grade points {} outside normal range for course: {}", gradePoints,
                            grade.courseId());
                }

                totalGradePoints += gradePoints * credits;
                totalCredits += credits;
                validGrades++;

                log.debug("Grade processed - Course: {}, Grade: {}, Points: {}, Credits: {}",
                        grade.courseId(), grade.grade(), gradePoints, credits);
            }

            if (totalCredits == 0) {
                throw new IllegalArgumentException("Total credits cannot be zero");
            }

            double gpa = totalGradePoints / totalCredits;

            Map<String, Object> result = new HashMap<>();
            result.put("gpa", Math.round(gpa * 100.0) / 100.0);
            result.put("total_credits", totalCredits);
            result.put("total_grade_points", Math.round(totalGradePoints * 100.0) / 100.0);
            result.put("courses_count", validGrades);
            result.put("student_id", grades.isEmpty() ? null : grades.get(0).studentId());

            log.info("GPA calculated: {} for student {} with {} credits across {} courses",
                    result.get("gpa"), result.get("student_id"), totalCredits, validGrades);
            return result;

        } catch (Exception e) {
            log.error("Error calculating GPA", e);
            throw new RuntimeException("GPA calculation failed: " + e.getMessage());
        }
    }

    @Tool(description = "Calculate CGPA for a specific student using all their grade records")
    public Map<String, Object> calculateStudentCGPA(
            @ToolParam(description = "Student ID to calculate CGPA for") String studentId,
            @ToolParam(description = "JSON array of all grade objects for the student")
            List<Grade> allGrades, @ToolParam(
                    description = "Optional: JSON array of course objects with courseId and credits")
            List<Map<String, Object>> courses, ToolContext toolContext) {

        log.info("CGPA calculation requested for student: {}", studentId);

        try {
            // Filter grades for the specific student
            List<Grade> studentGrades =
                    allGrades.stream().filter(grade -> studentId.equals(grade.studentId()))
                            .toList();

            if (studentGrades.isEmpty()) {
                throw new IllegalArgumentException("No grades found for student: " + studentId);
            }

            // Use the existing calculateGPA method
            Map<String, Object> result = calculateGPA(studentGrades, courses, toolContext);

            // Rename GPA to CGPA for clarity
            result.put("cgpa", result.get("gpa"));
            result.put("calculation_type", "cumulative");

            log.info("CGPA calculated: {} for student: {}", result.get("cgpa"), studentId);
            return result;

        } catch (Exception e) {
            log.error("Error calculating CGPA for student: {}", studentId, e);
            throw new RuntimeException("CGPA calculation failed: " + e.getMessage());
        }
    }

    @Tool(description = "Convert letter grades to GPA points using standard 4.0 scale conversion")
    public Map<String, Object> convertLetterGradesToGPA(
            @ToolParam(description = "JSON array of grade objects with letter grades")
            List<Map<String, Object>> letterGrades, ToolContext toolContext) {

        log.info("Converting {} letter grades to GPA points", letterGrades.size());

        try {
            if (letterGrades == null || letterGrades.isEmpty()) {
                throw new IllegalArgumentException("No letter grades provided");
            }

            List<Map<String, Object>> convertedGrades = letterGrades.stream().map(grade -> {
                String letterGrade = (String) grade.get("grade");
                double gpaPoints = convertLetterGradeToPoints(letterGrade);

                Map<String, Object> converted = new HashMap<>(grade);
                converted.put("gpa_points", gpaPoints);
                converted.put("original_grade", letterGrade);
                return converted;
            }).toList();

            Map<String, Object> result = new HashMap<>();
            result.put("converted_grades", convertedGrades);
            result.put("conversion_scale", "4.0 scale");
            result.put("grades_converted", convertedGrades.size());

            log.info("Successfully converted {} letter grades to GPA points",
                    convertedGrades.size());
            return result;

        } catch (Exception e) {
            log.error("Error converting letter grades to GPA", e);
            throw new RuntimeException("Letter grade to GPA conversion failed: " + e.getMessage());
        }
    }

    @Tool(description = "Calculate class average GPA for a specific course")
    public Map<String, Object> calculateCourseGPA(
            @ToolParam(description = "Course ID to calculate average GPA for") String courseId,
            @ToolParam(description = "JSON array of all grade objects") List<Grade> allGrades,
            @ToolParam(description = "Optional: course credits") Integer courseCredits,
            ToolContext toolContext) {

        log.info("Course GPA calculation requested for course: {}", courseId);

        try {
            // Filter grades for the specific course
            List<Grade> courseGrades =
                    allGrades.stream().filter(grade -> courseId.equals(grade.courseId()))
                            .filter(grade -> grade.points() != null).toList();

            if (courseGrades.isEmpty()) {
                throw new IllegalArgumentException("No valid grades found for course: " + courseId);
            }

            double totalPoints = courseGrades.stream().mapToDouble(Grade::points).sum();

            double averageGPA = totalPoints / courseGrades.size();

            Map<String, Object> gradeDistribution = new HashMap<>();
            courseGrades.forEach(grade -> {
                String letterGrade = grade.grade();
                gradeDistribution.merge(letterGrade, 1, (oldVal, newVal) -> (Integer) oldVal + 1);
            });

            Map<String, Object> result = new HashMap<>();
            result.put("course_id", courseId);
            result.put("average_gpa", Math.round(averageGPA * 100.0) / 100.0);
            result.put("total_students", courseGrades.size());
            result.put("course_credits", courseCredits);
            result.put("grade_distribution", gradeDistribution);

            log.info("Course GPA calculated: {} for course: {} with {} students",
                    result.get("average_gpa"), courseId, courseGrades.size());
            return result;

        } catch (Exception e) {
            log.error("Error calculating course GPA for: {}", courseId, e);
            throw new RuntimeException("Course GPA calculation failed: " + e.getMessage());
        }
    }

    @Tool(description = "Get grade statistics and analysis for a student or course")
    public Map<String, Object> getGradeStatistics(
            @ToolParam(description = "Filter type: 'student' or 'course'") String filterType,
            @ToolParam(description = "Filter value: studentId or courseId") String filterValue,
            @ToolParam(description = "JSON array of all grade objects") List<Grade> allGrades,
            ToolContext toolContext) {

        log.info("Grade statistics requested for {} : {}", filterType, filterValue);

        try {
            List<Grade> filteredGrades;

            if ("student".equalsIgnoreCase(filterType)) {
                filteredGrades =
                        allGrades.stream().filter(grade -> filterValue.equals(grade.studentId()))
                                .toList();
            } else if ("course".equalsIgnoreCase(filterType)) {
                filteredGrades =
                        allGrades.stream().filter(grade -> filterValue.equals(grade.courseId()))
                                .toList();
            } else {
                throw new IllegalArgumentException("Filter type must be 'student' or 'course'");
            }

            if (filteredGrades.isEmpty()) {
                throw new IllegalArgumentException(
                        "No grades found for " + filterType + ": " + filterValue);
            }

            // Calculate statistics
            List<Double> points =
                    filteredGrades.stream().map(Grade::points).filter(Objects::nonNull).sorted()
                            .toList();

            double average = points.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double highest = points.isEmpty() ? 0.0 : points.get(points.size() - 1);
            double lowest = points.isEmpty() ? 0.0 : points.get(0);

            Map<String, Integer> gradeFrequency = new HashMap<>();
            filteredGrades.forEach(grade -> {
                gradeFrequency.merge(grade.grade(), 1, Integer::sum);
            });

            Map<String, Object> result = new HashMap<>();
            result.put("filter_type", filterType);
            result.put("filter_value", filterValue);
            result.put("total_grades", filteredGrades.size());
            result.put("average_points", Math.round(average * 100.0) / 100.0);
            result.put("highest_points", highest);
            result.put("lowest_points", lowest);
            result.put("grade_frequency", gradeFrequency);

            log.info("Grade statistics calculated for {} : {} - {} grades, avg: {}", filterType,
                    filterValue, filteredGrades.size(), average);
            return result;

        } catch (Exception e) {
            log.error("Error calculating grade statistics for {} : {}", filterType, filterValue, e);
            throw new RuntimeException("Grade statistics calculation failed: " + e.getMessage());
        }
    }

    private double convertLetterGradeToPoints(String letterGrade) {
        if (letterGrade == null)
            return 0.0;

        return switch (letterGrade.toUpperCase().trim()) {
            case "A+", "A" -> 4.0;
            case "A-" -> 3.7;
            case "B+" -> 3.3;
            case "B" -> 3.0;
            case "B-" -> 2.7;
            case "C+" -> 2.3;
            case "C" -> 2.0;
            case "C-" -> 1.7;
            case "D+" -> 1.3;
            case "D" -> 1.0;
            case "D-" -> 0.7;
            case "F" -> 0.0;
            default -> {
                log.warn("Unknown letter grade: {}, defaulting to 0.0", letterGrade);
                yield 0.0;
            }
        };
    }
}
