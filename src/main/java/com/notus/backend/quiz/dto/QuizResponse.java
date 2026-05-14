package com.notus.backend.quiz.dto;

import java.util.List;

public class QuizResponse {
    private String title;
    private String description;
    private List<QuestionDto> questions;
    private Long groupId;
    private Boolean countAsGrade;
    private Integer gradeWeight;
    private String semester;

    public QuizResponse() {
    }

    public QuizResponse(String title, String description, List<QuestionDto> questions) {
        this.title = title;
        this.description = description;
        this.questions = questions;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<QuestionDto> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionDto> questions) {
        this.questions = questions;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Boolean getCountAsGrade() {
        return countAsGrade;
    }

    public void setCountAsGrade(Boolean countAsGrade) {
        this.countAsGrade = countAsGrade;
    }

    public Integer getGradeWeight() {
        return gradeWeight;
    }

    public void setGradeWeight(Integer gradeWeight) {
        this.gradeWeight = gradeWeight;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }
}
