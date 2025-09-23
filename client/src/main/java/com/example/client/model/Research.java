package com.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Research {
    private String researchId;
    private String title;
    private String abstractText;
    private String authors;
    private Integer year;
    private String keywords;
}
