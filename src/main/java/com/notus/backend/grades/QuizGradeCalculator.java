package com.notus.backend.grades;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class QuizGradeCalculator {

    public String gradeForPercentage(double percentage) {
        if (percentage < 0 || percentage > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wynik quizu musi być w zakresie 0-100%.");
        }
        if (percentage >= 95) return "6";
        if (percentage >= 85) return "5";
        if (percentage >= 70) return "4";
        if (percentage >= 55) return "3";
        if (percentage >= 40) return "2";
        return "1";
    }
}
