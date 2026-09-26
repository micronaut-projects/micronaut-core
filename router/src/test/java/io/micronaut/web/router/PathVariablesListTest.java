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
package io.micronaut.web.router;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.exceptions.UnsatisfiedPathVariableRouteException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The list accessors of {@link PathVariables}, which split and convert the values like a
 * {@code List} path variable argument of a controller method.
 */
class PathVariablesListTest {

    private final Router router = router(routes -> {
        routes.GET("/files{/path*}", (request, pathVariables) -> HttpResponse.ok());
        routes.GET("/tags/{tags}", (request, pathVariables) -> HttpResponse.ok());
    });

    @Test
    void theValuesOfAnExplodedVariable() {
        PathVariables variables = variables("/files/a,b,c");
        assertEquals(controllerList("/files/a,b,c", "path", Argument.STRING), variables.getStrings("path"));
        assertEquals(variables.getStrings("path"), variables.findList("path", String.class).orElseThrow());
    }

    @Test
    void theValuesOfACommaSeparatedValue() {
        PathVariables variables = variables("/tags/red,green,blue");
        assertEquals(List.of("red", "green", "blue"), variables.getStrings("tags"));
        assertEquals(controllerList("/tags/red,green,blue", "tags", Argument.STRING), variables.getStrings("tags"));
    }

    @Test
    void aSingleValueIsAListOfOne() {
        assertEquals(List.of("red"), variables("/tags/red").getStrings("tags"));
    }

    @Test
    void theValuesConvertToAType() {
        PathVariables variables = variables("/tags/1,2,3");
        assertEquals(List.of(1, 2, 3), variables.getList("tags", Integer.class));
        assertEquals(List.of(1L, 2L, 3L), variables.getList("tags", Argument.LONG));
        assertEquals(Optional.of(List.of(1, 2, 3)), variables.findList("tags", Integer.class));
    }

    @Test
    void anElementThatDoesNotConvertIsLeftOutLikeForAController() {
        PathVariables variables = variables("/tags/1,x,3");
        // the conversion service leaves out the element, for a controller argument too
        assertEquals(controllerList("/tags/1,x,3", "tags", Argument.INT), variables.getList("tags", Integer.class));
        assertEquals(List.of(1, 3), variables.getList("tags", Integer.class));
        assertEquals(Optional.of(List.of(1, 3)), variables.findList("tags", Integer.class));
    }

    @Test
    void aMissingVariable() {
        PathVariables variables = variables("/tags/red");
        assertThrows(UnsatisfiedPathVariableRouteException.class, () -> variables.getStrings("missing"));
        assertThrows(UnsatisfiedPathVariableRouteException.class, () -> variables.getList("missing", Integer.class));
        assertEquals(Optional.empty(), variables.findList("missing", Integer.class));
    }

    private PathVariables variables(String path) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match, path);
        return new DefaultPathVariables(match.getVariableValues(), ConversionService.SHARED);
    }

    /**
     * The list a {@code List} path variable argument of a controller method gets: the variable
     * values converted with the conversion service, see {@code PathVariableAnnotationBinder}.
     */
    private <T> List<T> controllerList(String path, String name, Argument<T> type) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match, path);
        return io.micronaut.core.convert.value.ConvertibleValues.of(match.getVariableValues(), ConversionService.SHARED)
            .get(name, io.micronaut.core.convert.ConversionContext.of(Argument.listOf(type)))
            .orElseThrow();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
