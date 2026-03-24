package io.micronaut.jackson.databind;

import tools.jackson.databind.ObjectMapper;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.jackson.JacksonConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonConfigurationConstructTypeTest {

    @Test
    void constructTypePreservesParameterizedMapArgumentCreatedFromType() {
        Argument<Map<String, String>> derived = new DefaultArgument<Map<String, String>>((Type) null, null, null) {
        };

        var jacksonType = JacksonConfiguration.constructType(derived, new ObjectMapper().getTypeFactory());

        assertTrue(jacksonType.isMapLikeType());
        assertEquals(Map.class, jacksonType.getRawClass());
        assertEquals(String.class, jacksonType.getKeyType().getRawClass());
        assertEquals(String.class, jacksonType.getContentType().getRawClass());
    }
}
