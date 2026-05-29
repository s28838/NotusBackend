package com.notus.backend.history.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionStudentResultDto {
    private Long studentId;
    private String studentName;
    private boolean attended;
    private Long submissionId;
    private Integer quizScore;
    private Integer quizTotal;
    private boolean pendingOpenReview;
}
