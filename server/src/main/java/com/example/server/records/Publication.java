package com.example.server.records;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Publication(@JsonProperty("title") String title,
                          @JsonProperty("authors") List<String> authors,
                          @JsonProperty("journal") String journal,
                          @JsonProperty("year") Integer year, @JsonProperty("doi") String doi,
                          @JsonProperty("abstract") String abstractText,
                          @JsonProperty("url") String url) {
}
