package com.notus.backend.grades;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class GradeAverageCalculator {

    public BigDecimal weightedAverage(List<Grade> grades) {
        List<Grade> activeGrades = grades == null ? List.of() : grades.stream()
                .filter(grade -> grade.getDeletedAt() == null)
                .filter(grade -> grade.getNumericValue() != null)
                .filter(grade -> grade.getWeight() != null && grade.getWeight() > 0)
                .toList();

        if (activeGrades.isEmpty()) {
            return null;
        }

        BigDecimal weightedSum = BigDecimal.ZERO;
        int weightSum = 0;
        for (Grade grade : activeGrades) {
            weightedSum = weightedSum.add(grade.getNumericValue().multiply(BigDecimal.valueOf(grade.getWeight())));
            weightSum += grade.getWeight();
        }

        if (weightSum == 0) {
            return null;
        }
        return weightedSum.divide(BigDecimal.valueOf(weightSum), 2, RoundingMode.HALF_UP);
    }
}
