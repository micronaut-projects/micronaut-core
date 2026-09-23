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

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.ClosedRouteBuilderTest;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The builder of a route table of handler routes is closed when the callback that declares the
 * routes returned: a route declared on it later, which the table would not have, fails.
 */
class ClosedRouteTableBuilderTest {

    private final RouteTableFactory tables = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, null);

    @Test
    void theBuilderOfARouteTableIsClosedWhenTheCallbackReturned() {
        AtomicReference<HttpRouteBuilder> kept = new AtomicReference<>();
        tables.buildHttpRoutes(routes -> {
            kept.set(routes);
            routes.GET("/declared", ClosedRouteTableBuilderTest::ok);
        });
        ClosedRouteBuilderTest.assertEveryDeclarationFails(kept.get());
    }

    @Test
    void theBuilderOfALocatedRouteTableIsClosedWhenTheCallbackReturned() {
        AtomicReference<HttpRouteBuilder> kept = new AtomicReference<>();
        tables.buildLocatedHttpRoutes(routes -> {
            kept.set(routes);
            routes.GET("/declared", ClosedRouteTableBuilderTest::ok);
        });
        ClosedRouteBuilderTest.assertEveryDeclarationFails(kept.get());
    }

    @Test
    void theBuilderIsClosedWhenTheCallbackFails() {
        AtomicReference<HttpRouteBuilder> kept = new AtomicReference<>();
        assertThrows(UnsupportedOperationException.class, () -> tables.buildHttpRoutes(routes -> {
            kept.set(routes);
            throw new UnsupportedOperationException("failed");
        }));
        ClosedRouteBuilderTest.assertClosed(() -> kept.get().GET("/late", ClosedRouteTableBuilderTest::ok));
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }
}
