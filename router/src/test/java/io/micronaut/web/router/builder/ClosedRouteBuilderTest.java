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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.Router;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The builder of the {@link HttpRoutes} beans is closed once the routes were
 * declared: a route declared on it later, which the router would not have, fails instead of being
 * dropped.
 */
public class ClosedRouteBuilderTest {

    @Test
    void anHttpRoutesBeanThatKeepsTheBuilderCannotDeclareRoutesLater() {
        AtomicReference<HttpRouteBuilder> kept = new AtomicReference<>();
        HttpRoutes bean = routes -> {
            kept.set(routes);
            routes.GET("/declared", ClosedRouteBuilderTest::ok);
        };
        HttpRoutesAssembly assembly = new HttpRoutesAssembly(ExecutionHandleLocator.EMPTY, ConversionService.SHARED, List.of(bean), null);

        assertEveryDeclarationFails(kept.get());
        Router router = new DefaultRouter(List.of(), List.of(assembly));
        assertNotNull(router.findClosest(HttpRequest.GET("/declared")));
        assertNull(router.findClosest(HttpRequest.GET("/late")));
    }

    /**
     * @param routes A closed builder
     */
    public static void assertEveryDeclarationFails(HttpRouteBuilder routes) {
        assertClosed(() -> routes.GET("/late", ClosedRouteBuilderTest::ok));
        assertClosed(() -> routes.handle(Set.of(HttpMethod.GET, HttpMethod.POST), "/late", ClosedRouteBuilderTest::ok));
        assertClosed(() -> routes.handle("PROPFIND", "/late", ClosedRouteBuilderTest::ok));
        assertClosed(() -> routes.POST("/late", Argument.of(String.class), (request, pathVariables, body) -> HttpResponse.ok()));
        assertClosed(() -> routes.asyncGET("/late", (request, pathVariables) -> CompletableFuture.completedFuture(HttpResponse.ok())));
        assertClosed(() -> routes.POST("/late", (FormRequestHandler) (request, pathVariables, form) -> HttpResponse.ok()));
        assertClosed(() -> routes.handle(RouteDeclaration.of(HttpMethod.GET, "/late"), ClosedRouteBuilderTest::ok));
        assertClosed(() -> routes.error(IllegalStateException.class, (request, error) -> HttpResponse.ok()));
        assertClosed(() -> routes.status(HttpStatus.NOT_FOUND, request -> HttpResponse.ok()));
        assertClosed(() -> routes.filter("/**"));
        assertClosed(() -> routes.group(group -> group.GET("/late", ClosedRouteBuilderTest::ok)));
        assertClosed(() -> routes.path("/late", group -> group.GET("/x", ClosedRouteBuilderTest::ok)));
        assertClosed(() -> routes.locate("/late", (request, pathVariables) -> "target", target -> null));
    }

    /**
     * @param declaration A declaration on a closed builder
     */
    public static void assertClosed(Executable declaration) {
        IllegalStateException e = assertThrows(IllegalStateException.class, declaration);
        assertEquals("The route builder is closed: declare the routes inside HttpRoutes.routes(...), "
            + "or inside LocatedRoutes.routes(...), not after it returned", e.getMessage());
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok(Map.of());
    }
}
