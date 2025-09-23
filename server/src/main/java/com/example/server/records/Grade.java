package com.example.server.records;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Grade(@JsonProperty("student_id") String studentId,
                    @JsonProperty("course_id") String courseId, @JsonProperty("grade") String grade,
                    @JsonProperty("points") Double points,
                    @JsonProperty("course_name") String courseName,
                    @JsonProperty("credits") Integer credits) {
}
