package io.micronaut.core.naming.conventions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MethodConventionTest {

    @ParameterizedTest
    @EnumSource(MethodConvention.class)
    void forMethodFindsEveryConventionIgnoringCase(MethodConvention convention) {
        assertEquals(Optional.of(convention), MethodConvention.forMethod(convention.name().toLowerCase(Locale.ENGLISH)));
    }

    @Test
    void forMethodIsEmptyForAnUnknownName() {
        assertEquals(Optional.empty(), MethodConvention.forMethod("unknown"));
    }
}
