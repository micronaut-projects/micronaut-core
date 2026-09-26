package io.micronaut.web.router;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.exceptions.UnsatisfiedPathVariableRouteException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutePathVariablesTest {

    private final PathVariables pathVariables = new RoutePathVariables(Map.of("id", "5", "ids", "1,2"), ConversionService.SHARED);

    @Test
    void readsTheVariables() {
        assertEquals(5L, pathVariables.getLong("id"));
        assertEquals(List.of(1, 2), pathVariables.getList("ids", Integer.class));
    }

    @Test
    void aMissingVariableFailsLikeAControllerPathVariable() {
        UnsatisfiedPathVariableRouteException e = assertThrows(UnsatisfiedPathVariableRouteException.class, () -> pathVariables.getInt("other"));
        assertEquals("other", e.getPathVariableName());
        assertEquals("Required PathVariable [other] not specified", e.getMessage());
    }

    @Test
    void aValueThatDoesNotConvertIsAConversionError() {
        assertThrows(ConversionErrorException.class, () -> pathVariables.getInt("ids"));
    }
}
