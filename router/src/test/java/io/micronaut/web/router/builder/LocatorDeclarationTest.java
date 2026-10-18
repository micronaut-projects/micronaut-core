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

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A locator route declared with {@link HttpRouteScope#locator(String)} is ended by one terminal
 * of its body stage: a declaration without one fails the startup instead of being dropped.
 */
class LocatorDeclarationTest {

    private static final LocatedRoutes<Object> ROUTES = new LocatedRoutes<>() {
        @Override
        public Argument<Object> targetType() {
            return Argument.OBJECT_ARGUMENT;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<Object> located) {
            located.GET("/", (request, pathVariables) -> HttpResponse.ok());
        }
    };

    @Test
    void aLocatorWithoutATerminalFailsTheStartup() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> assembly(new UnendedRoutes()));

        assertEquals("The route locator /accounts declared by LocatorDeclarationTest.UnendedRoutes has no handler: "
            + "end it with locate or locateAsync", failure.getMessage());
    }

    @Test
    void aRouteAndALocatorWithoutATerminalNameTheTerminalsOfBoth() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> assembly(new BothUnendedRoutes()));

        assertEquals("The routes GET /items declared by LocatorDeclarationTest.BothUnendedRoutes, locator /accounts declared by "
            + "LocatorDeclarationTest.BothUnendedRoutes have no handler: end each with handle, handleAsync or respond, or locate or locateAsync",
            failure.getMessage());
    }

    @Test
    void aLocatorIsEndedOnce() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> assembly(new TwiceEndedRoutes()));

        assertEquals("The route locator /accounts declared by LocatorDeclarationTest.TwiceEndedRoutes was already ended: "
            + "give its settings before its one terminal, locate or locateAsync", failure.getMessage());
    }

    @Test
    void anEndedLocatorStartsTheApplication() {
        assembly(routes -> routes.locator("/accounts").form().locate((request, pathVariables, form) -> "account", ROUTES));
    }

    private static HttpRoutesAssembly assembly(HttpRoutes... routes) {
        return new HttpRoutesAssembly(ExecutionHandleLocator.EMPTY, ConversionService.SHARED, List.of(routes), null);
    }

    static final class UnendedRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.locator("/accounts").form();
        }
    }

    static final class BothUnendedRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/items");
            routes.locator("/accounts").body(String.class);
        }
    }

    static final class TwiceEndedRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            LocatorBodySpec<String> body = routes.locator("/accounts").body(String.class);
            body.locate((request, pathVariables, text) -> text, ROUTES);
            body.locate((request, pathVariables, text) -> text, ROUTES);
        }
    }
}
