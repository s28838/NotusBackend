package com.notus.backend.grades;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradeDto {
    private Long id;
    private Long groupId;
    private String groupName;
    private String subject;
    private String value;
    private LocalDateTime issueDate;
    @JsonProperty("isNew")
    private boolean isNew;
}
