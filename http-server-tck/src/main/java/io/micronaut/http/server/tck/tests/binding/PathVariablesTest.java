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
package io.micronaut.http.server.tck.tests.binding;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.PathVariables;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestAttribute;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.TreeSet;

import static io.micronaut.http.tck.TestScenario.asserts;

/**
 * A {@link PathVariables} parameter of a controller method, and of a request filter method of a
 * server filter that runs after the request is routed: all variables of the route.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class PathVariablesTest {
    public static final String SPEC_NAME = "PathVariablesTest";

    @Test
    void aControllerReadsThePathVariablesTyped() throws IOException {
        ok("/path-variables/items/5/page/2", "[id, page] item 5 page 2 size 10 true");
    }

    @Test
    void aControllerReadsAListVariable() throws IOException {
        ok("/path-variables/tags/a,b,c", "[a, b, c]");
        ok("/path-variables/numbers/1,2,3", "6");
    }

    @Test
    void aMissingVariableIsBadRequest() throws IOException {
        badRequest("/path-variables/missing/5");
    }

    @Test
    void aVariableThatDoesNotConvertIsBadRequest() throws IOException {
        badRequest("/path-variables/items/abc/page/2");
    }

    @Test
    void aListVariableWithAnElementThatDoesNotConvertIsBadRequest() throws IOException {
        // not the elements that convert, without the others
        badRequest("/path-variables/numbers/1,bad");
        badRequest("/path-variables/numbers-typed/1,bad");
        ok("/path-variables/numbers-typed/1,2", "[1, 2]");
    }

    @Test
    void aServerFilterReadsThePathVariablesAfterRouting() throws IOException {
        ok("/path-variables/filtered/7", "filter:7 controller:7");
    }

    @Test
    void aNullablePathVariablesOfAFilterIsNullWhenNoRouteMatched() throws IOException {
        ok("/path-variables/unrouted/7", "no route");
        ok("/path-variables/items/5/page/2?filter=nullable", "filter:[id, page]");
    }

    private static void ok(String uri, String body) throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET(uri),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(body)
                .build()));
    }

    private static void badRequest(String uri) throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET(uri),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build()));
    }

    @Controller("/path-variables")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PathVariablesController {

        @Get("/items/{id}/page/{page}")
        String item(PathVariables pathVariables) {
            return new TreeSet<>(pathVariables.names()) + " item " + pathVariables.getLong("id")
                + " page " + pathVariables.getInt("page")
                + " size " + pathVariables.getInt("size", 10)
                + " " + pathVariables.findInt("size").isEmpty();
        }

        @Get("/tags/{tags}")
        String tags(PathVariables pathVariables) {
            return pathVariables.getStrings("tags").toString();
        }

        @Get("/numbers/{numbers}")
        String numbers(PathVariables pathVariables) {
            List<Integer> numbers = pathVariables.getList("numbers", Integer.class);
            return String.valueOf(numbers.stream().mapToInt(Integer::intValue).sum());
        }

        @Get("/numbers-typed/{numbers}")
        String numbersTyped(PathVariables pathVariables) {
            return pathVariables.get("numbers", Argument.listOf(Integer.class)).toString();
        }

        @Get("/missing/{id}")
        String missing(PathVariables pathVariables) {
            return pathVariables.getString("other");
        }

        @Get("/filtered/{id}")
        String filtered(PathVariables pathVariables, @RequestAttribute("path-variables-filter") String filter) {
            return filter + " controller:" + pathVariables.getInt("id");
        }
    }

    @ServerFilter({"/path-variables/unrouted/**", "/path-variables/items/**"})
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class NullablePathVariablesFilter {

        @RequestFilter
        @Nullable HttpResponse<?> filter(HttpRequest<?> request, @Nullable PathVariables pathVariables) {
            if (pathVariables == null) {
                return HttpResponse.ok("no route");
            }
            if (request.getParameters().contains("filter")) {
                return HttpResponse.ok("filter:" + new TreeSet<>(pathVariables.names()));
            }
            return null;
        }
    }

    @ServerFilter("/path-variables/filtered/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PathVariablesFilter {

        @RequestFilter
        void filter(MutableHttpRequest<?> request, PathVariables pathVariables) {
            request.setAttribute("path-variables-filter", "filter:" + pathVariables.getString("id"));
        }
    }
}
