/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.web.router.builder;

import io.micronaut.core.convert.ConversionService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The path variables a handler receives are a read-only view of the variables of the match, which
 * the other arguments of the route are bound from too.
 */
class DefaultPathVariablesTest {

    @Test
    void theVariablesOfTheMatchCannotBeChangedThroughThePathVariables() {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("id", "5");
        match.put("item", "3");
        DefaultPathVariables pathVariables = new DefaultPathVariables(match, ConversionService.SHARED);

        Set<String> names = pathVariables.names();
        Map<String, Object> values = pathVariables.values();
        assertThrows(UnsupportedOperationException.class, () -> names.remove("id"));
        assertThrows(UnsupportedOperationException.class, names::clear);
        assertThrows(UnsupportedOperationException.class, () -> values.put("id", "6"));
        assertThrows(UnsupportedOperationException.class, () -> values.remove("item"));
        assertEquals(Map.of("id", "5", "item", "3"), match);
        assertEquals(Set.of("id", "item"), pathVariables.names());
        assertEquals(5L, pathVariables.getLong("id"));
    }

    @Test
    void theStringSaysWhatTheVariablesAre() {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("id", "5");
        assertEquals("PathVariables{id=5}", new DefaultPathVariables(match, ConversionService.SHARED).toString());
        assertEquals("PathVariables{id=5} of order 5", new DefaultPathVariables(match, ConversionService.SHARED, "order 5").toString());
    }

    @Test
    void theRawViewKeepsTheTargetAndTheNegotiatedMediaType() {
        DefaultPathVariables pathVariables = new DefaultPathVariables(Map.of("id", "a/b"), Map.of("id", "a%2Fb"), ConversionService.SHARED,
            "order 5", io.micronaut.http.MediaType.APPLICATION_JSON_TYPE);
        io.micronaut.http.PathVariables raw = pathVariables.raw();

        assertEquals("a%2Fb", raw.getString("id"));
        assertEquals("order 5", LocatedRoutes.locatedTarget(raw));
        assertEquals("order 5", LocatedRoutes.locatedTarget(raw, String.class));
        assertEquals(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE, io.micronaut.web.router.spi.RouteMatchSelector.selectedMediaType(raw));
        // a missing variable of the raw view fails like one of the route
        assertThrows(io.micronaut.web.router.exceptions.UnsatisfiedPathVariableRouteException.class, () -> raw.getString("missing"));
    }
}
