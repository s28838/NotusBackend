package com.notus.backend.grades;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GradeAverageCalculatorTest {

    private final GradeAverageCalculator calculator = new GradeAverageCalculator();

    @Test
    void calculatesWeightedAverage() {
        Grade five = grade("5", 5.0, 1);
        Grade four = grade("4", 4.0, 2);

        assertEquals(BigDecimal.valueOf(4.33), calculator.weightedAverage(List.of(five, four)));
    }

    @Test
    void returnsNullForEmptyList() {
        assertNull(calculator.weightedAverage(List.of()));
    }

    @Test
    void ignoresSoftDeletedGrades() {
        Grade active = grade("5", 5.0, 1);
        Grade deleted = grade("1", 1.0, 1);
        deleted.setDeletedAt(LocalDateTime.now());

        assertEquals(BigDecimal.valueOf(5.00).setScale(2), calculator.weightedAverage(List.of(active, deleted)));
    }

    private Grade grade(String value, double numeric, int weight) {
        Grade grade = new Grade();
        grade.setValue(value);
        grade.setNumericValue(BigDecimal.valueOf(numeric));
        grade.setWeight(weight);
        return grade;
    }
}
