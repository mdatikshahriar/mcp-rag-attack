package com.example.server.records;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Student(@JsonProperty("student_id") String studentId,
                      @JsonProperty("name") String name,
                      @JsonProperty("grades") List<Grade> grades) {
}
