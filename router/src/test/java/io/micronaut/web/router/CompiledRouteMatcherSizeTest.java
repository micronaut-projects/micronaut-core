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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.spi.CompiledRouteMatcher;
import io.micronaut.web.router.spi.IndexedRouteDeclaration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A generated URL parser that reports fewer path variables than a route it answers has, or a
 * negative number, does not fail every request with an {@link ArrayIndexOutOfBoundsException}.
 */
class CompiledRouteMatcherSizeTest {

    @Test
    void aMatcherThatUnderReportsItsVariablesStillMatchesTheRoute() {
        Router router = router(routes -> routes.handle(UnderReported.FIND, (request, pathVariables) -> HttpResponse.ok(pathVariables.getString("id"))));

        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/pets/5"));
        assertNotNull(match);
        assertEquals(Map.of("id", "5"), match.getVariableValues());
    }

    @Test
    void aMatcherWithANegativeNumberOfVariablesFailsWhenTheRouterIsBuilt() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> router(routes -> routes.handle(Negative.FIND, (request, pathVariables) -> HttpResponse.ok())));
        assertTrue(e.getMessage().contains("has a negative maxVariables(): -1"), e.getMessage());
        assertTrue(e.getMessage().contains(Negative.class.getName() + ".FIND"), e.getMessage());
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    /**
     * Captures {@code /pets/{id}}, but reports no variables.
     */
    enum UnderReported implements IndexedRouteDeclaration {
        FIND;

        private static final CompiledRouteMatcher MATCHER = new PetsMatcher(0);

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public String uriTemplate() {
            return "/pets/{id}";
        }

        @Override
        public String requiredPathPrefix() {
            return "/pets/";
        }

        @Override
        public int rawLength() {
            return 6;
        }

        @Override
        public int pathVariableCount() {
            return 1;
        }

        @Override
        public @Nullable CompiledRouteMatcher matcher() {
            return MATCHER;
        }
    }

    /**
     * Reports a negative number of variables.
     */
    enum Negative implements IndexedRouteDeclaration {
        FIND;

        private static final CompiledRouteMatcher MATCHER = new PetsMatcher(-1);

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public String uriTemplate() {
            return "/pets/{id}";
        }

        @Override
        public String requiredPathPrefix() {
            return "/pets/";
        }

        @Override
        public int rawLength() {
            return 6;
        }

        @Override
        public int pathVariableCount() {
            return 1;
        }

        @Override
        public @Nullable CompiledRouteMatcher matcher() {
            return MATCHER;
        }
    }

    private record PetsMatcher(int maxVariables) implements CompiledRouteMatcher {
        @Override
        public int match(HttpMethod method, String path, String[] variables) {
            if (method == HttpMethod.GET && path.startsWith("/pets/") && path.indexOf('/', 6) < 0 && path.length() > 6) {
                variables[0] = path.substring(6);
                return 0;
            }
            return -1;
        }
    }
}
