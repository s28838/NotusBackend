package com.notus.backend.grades;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuizGradeCalculatorTest {

    private final QuizGradeCalculator calculator = new QuizGradeCalculator();

    @Test
    void mapsPercentageThresholds() {
        assertEquals("1", calculator.gradeForPercentage(0));
        assertEquals("1", calculator.gradeForPercentage(39));
        assertEquals("2", calculator.gradeForPercentage(40));
        assertEquals("2", calculator.gradeForPercentage(54));
        assertEquals("3", calculator.gradeForPercentage(55));
        assertEquals("3", calculator.gradeForPercentage(69));
        assertEquals("4", calculator.gradeForPercentage(70));
        assertEquals("4", calculator.gradeForPercentage(84));
        assertEquals("5", calculator.gradeForPercentage(85));
        assertEquals("5", calculator.gradeForPercentage(94));
        assertEquals("6", calculator.gradeForPercentage(95));
        assertEquals("6", calculator.gradeForPercentage(100));
    }

    @Test
    void rejectsInvalidPercentage() {
        assertThrows(ResponseStatusException.class, () -> calculator.gradeForPercentage(-1));
        assertThrows(ResponseStatusException.class, () -> calculator.gradeForPercentage(101));
    }
}
