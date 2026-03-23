package io.micronaut.jackson.databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.JacksonConfiguration;
import jakarta.ws.rs.core.GenericType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonConfigurationConstructTypeTest {

    @Test
    void constructTypePreservesParameterizedMapArgumentCreatedFromType() {
        var generic = new GenericType<Map<String, String>>() {
        };
        var derived = Argument.of(generic.getType());

        var jacksonType = JacksonConfiguration.constructType(derived, new ObjectMapper().getTypeFactory());

        assertTrue(jacksonType.isMapLikeType());
        assertEquals(Map.class, jacksonType.getRawClass());
        assertEquals(String.class, jacksonType.getKeyType().getRawClass());
        assertEquals(String.class, jacksonType.getContentType().getRawClass());
    }
}
