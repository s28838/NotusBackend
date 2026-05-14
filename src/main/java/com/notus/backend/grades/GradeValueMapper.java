package com.notus.backend.grades;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class GradeValueMapper {

    private static final Map<String, BigDecimal> VALUES = Map.ofEntries(
            Map.entry("6", BigDecimal.valueOf(6.0)),
            Map.entry("5+", BigDecimal.valueOf(5.5)),
            Map.entry("5", BigDecimal.valueOf(5.0)),
            Map.entry("5-", BigDecimal.valueOf(4.75)),
            Map.entry("4+", BigDecimal.valueOf(4.5)),
            Map.entry("4", BigDecimal.valueOf(4.0)),
            Map.entry("4-", BigDecimal.valueOf(3.75)),
            Map.entry("3+", BigDecimal.valueOf(3.5)),
            Map.entry("3", BigDecimal.valueOf(3.0)),
            Map.entry("3-", BigDecimal.valueOf(2.75)),
            Map.entry("2+", BigDecimal.valueOf(2.5)),
            Map.entry("2", BigDecimal.valueOf(2.0)),
            Map.entry("2-", BigDecimal.valueOf(1.75)),
            Map.entry("1", BigDecimal.valueOf(1.0))
    );

    public BigDecimal toNumeric(String value) {
        if (value == null || value.isBlank()) {
            throw invalidGrade();
        }
        BigDecimal numeric = VALUES.get(value.trim());
        if (numeric == null) {
            throw invalidGrade();
        }
        return numeric;
    }

    private ResponseStatusException invalidGrade() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowa wartość oceny.");
    }
}
