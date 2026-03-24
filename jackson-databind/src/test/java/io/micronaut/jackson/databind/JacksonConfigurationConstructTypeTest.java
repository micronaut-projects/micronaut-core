package io.micronaut.jackson.databind;

import tools.jackson.databind.ObjectMapper;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.jackson.JacksonConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonConfigurationConstructTypeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void constructTypePreservesParameterizedMapArgumentCreatedFromType() {
        Argument<Map<String, String>> derived = new DefaultArgument<Map<String, String>>((Type) null, null, null) {
        };

        var jacksonType = JacksonConfiguration.constructType(derived, objectMapper.getTypeFactory());

        assertTrue(jacksonType.isMapLikeType());
        assertEquals(Map.class, jacksonType.getRawClass());
        assertEquals(String.class, jacksonType.getKeyType().getRawClass());
        assertEquals(String.class, jacksonType.getContentType().getRawClass());
    }

    @Test
    void constructTypeFallsBackToRawMapTypeWhenTypeVariablesAreMissing() {
        var jacksonType = JacksonConfiguration.constructType(Argument.of(Map.class), objectMapper.getTypeFactory());

        assertTrue(jacksonType.isMapLikeType());
        assertEquals(Map.class, jacksonType.getRawClass());
    }

    @Test
    void constructTypeFallsBackToRawCollectionTypeWhenTypeVariablesAreMissing() {
        var jacksonType = JacksonConfiguration.constructType(Argument.of(List.class), objectMapper.getTypeFactory());

        assertTrue(jacksonType.isCollectionLikeType());
        assertEquals(List.class, jacksonType.getRawClass());
    }

    @Test
    void constructTypeFallsBackToRawReferenceTypeWhenTypeVariablesAreMissing() {
        var jacksonType = JacksonConfiguration.constructType(Argument.of(Optional.class), objectMapper.getTypeFactory());

        assertTrue(jacksonType.isReferenceType());
        assertEquals(Optional.class, jacksonType.getRawClass());
    }
}
