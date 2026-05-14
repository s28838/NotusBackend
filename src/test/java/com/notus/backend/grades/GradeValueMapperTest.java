package com.notus.backend.grades;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GradeValueMapperTest {

    private final GradeValueMapper mapper = new GradeValueMapper();

    @Test
    void mapsSupportedGradeValues() {
        assertEquals(BigDecimal.valueOf(6.0), mapper.toNumeric("6"));
        assertEquals(BigDecimal.valueOf(5.5), mapper.toNumeric("5+"));
        assertEquals(BigDecimal.valueOf(5.0), mapper.toNumeric("5"));
        assertEquals(BigDecimal.valueOf(4.75), mapper.toNumeric("5-"));
        assertEquals(BigDecimal.valueOf(4.5), mapper.toNumeric("4+"));
        assertEquals(BigDecimal.valueOf(4.0), mapper.toNumeric("4"));
        assertEquals(BigDecimal.valueOf(3.75), mapper.toNumeric("4-"));
        assertEquals(BigDecimal.valueOf(3.5), mapper.toNumeric("3+"));
        assertEquals(BigDecimal.valueOf(3.0), mapper.toNumeric("3"));
        assertEquals(BigDecimal.valueOf(2.75), mapper.toNumeric("3-"));
        assertEquals(BigDecimal.valueOf(2.5), mapper.toNumeric("2+"));
        assertEquals(BigDecimal.valueOf(2.0), mapper.toNumeric("2"));
        assertEquals(BigDecimal.valueOf(1.75), mapper.toNumeric("2-"));
        assertEquals(BigDecimal.valueOf(1.0), mapper.toNumeric("1"));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(ResponseStatusException.class, () -> mapper.toNumeric("abc"));
        assertThrows(ResponseStatusException.class, () -> mapper.toNumeric(""));
        assertThrows(ResponseStatusException.class, () -> mapper.toNumeric(null));
    }
}
