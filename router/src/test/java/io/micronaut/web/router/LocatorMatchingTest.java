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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The constraints of a located route see the variables of the prefix, a request with a custom
 * method is located, and a locator that fails is answered by the error routes of its group.
 */
class LocatorMatchingTest {

    @Test
    void aConstraintOfALocatedRouteSeesThePrefixVariables() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.GET("/items")
            .constrain(pathVariables -> "1".equals(pathVariables.get("id", String.class)))
            .handle((request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items));

        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/orders/1/items"));
        assertNotNull(match);
        assertEquals("1", match.getVariableValues().get("id"));
        assertNull(router.findClosest(HttpRequest.GET("/orders/2/items")));
    }

    @Test
    void aRequestWithACustomMethodIsLocatedOnceToFindTheAllowedMethods() {
        AtomicInteger calls = new AtomicInteger();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> {
            calls.incrementAndGet();
            return "order";
        }, target -> items));

        HttpRequest<?> request = HttpRequest.create(HttpMethod.CUSTOM, "/orders/1/items", "PROPFIND");
        assertNull(router.findClosest(request));
        // the located routes, not the locator routes
        List<UriRouteMatch<Object, Object>> allowed = router.findAny(request);
        assertFalse(allowed.isEmpty());
        for (UriRouteMatch<Object, Object> match : allowed) {
            assertTrue(Set.of("GET", "HEAD").contains(match.getRouteInfo().getHttpMethodName()), match.getRouteInfo().getHttpMethodName());
        }
        assertEquals(1, calls.get(), "the locator is called once per request");
    }

    @Test
    void aRequestWithACustomMethodMatchesTheLocatedRouteOfItsMethod() {
        AtomicInteger calls = new AtomicInteger();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> {
            located.GET("/items", (request, pathVariables) -> HttpResponse.ok());
            located.route("PROPFIND", "/items").handle((request, pathVariables) -> HttpResponse.ok());
        });
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> {
            calls.incrementAndGet();
            return "order";
        }, target -> items));

        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.create(HttpMethod.CUSTOM, "/orders/1/items", "PROPFIND"));
        assertNotNull(match);
        assertEquals("PROPFIND", match.getRouteInfo().getHttpMethodName());
        assertEquals("1", match.getVariableValues().get("id"));
        assertEquals(1, calls.get());
    }

    @Test
    void theErrorScopesOfAFailedLocatorAreItsGroups() {
        IllegalStateException failure = new IllegalStateException("no order");
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.path("/shop", shop -> {
            shop.locate("/sync/{id}", (request, pathVariables) -> {
                throw failure;
            }, target -> items);
            shop.locateAsync("/async/{id}", (request, pathVariables) -> CompletableFuture.failedFuture(failure), target -> items);
            shop.error(IllegalStateException.class, (request, error) -> HttpResponse.ok());
        }));

        for (String path : List.of("/shop/sync/1/items", "/shop/async/1/items")) {
            HttpRequest<?> request = HttpRequest.GET(path);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> router.findClosest(request)));
            assertNotNull(GroupErrorRoutes.findErrorRoute(request, null, failure), path);
            assertNull(GroupErrorRoutes.findErrorRoute(request, null, new IllegalStateException("another")), path);
        }
    }

    @Test
    void aConstraintOfALocatedRouteSeesTheDecodedPrefixVariables() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.GET("/items")
            .constrain(pathVariables -> "north shop".equals(pathVariables.get("id", String.class)))
            .handle((request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items));

        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/orders/north%20shop/items"));
        assertNotNull(match);
        assertEquals("north shop", match.getVariableValues().get("id"));
    }

    @Test
    void anAsynchronousLocatorThatThrowsIsCalledOnceAndAnsweredByItsGroup() {
        IllegalStateException failure = new IllegalStateException("no order");
        AtomicInteger calls = new AtomicInteger();
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.path("/shop", shop -> {
            shop.locateAsync("/async/{id}", (request, pathVariables) -> {
                calls.incrementAndGet();
                throw failure;
            }, target -> items);
            shop.error(IllegalStateException.class, (request, error) -> HttpResponse.ok());
        }));

        HttpRequest<?> request = HttpRequest.GET("/shop/async/1/items");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> router.findClosest(request)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> router.findClosest(request)));
        assertEquals(1, calls.get(), "the locator is called once per request");
        assertNotNull(GroupErrorRoutes.findErrorRoute(request, null, failure));
    }

    @Test
    void theFailureToSelectTheRoutesOfATargetIsAnsweredByTheGroupOfTheLocator() {
        IllegalStateException failure = new IllegalStateException("no routes");
        Router router = router(routes -> routes.path("/shop", shop -> {
            shop.locate("/sync/{id}", (request, pathVariables) -> "order", target -> {
                throw failure;
            });
            shop.locateAsync("/async/{id}", (request, pathVariables) -> CompletableFuture.completedFuture("order"), target -> {
                throw failure;
            });
            shop.error(IllegalStateException.class, (request, error) -> HttpResponse.ok());
        }));

        for (String path : List.of("/shop/sync/1/items", "/shop/async/1/items")) {
            HttpRequest<?> request = HttpRequest.GET(path);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> router.findClosest(request)));
            assertNotNull(GroupErrorRoutes.findErrorRoute(request, null, failure), path);
        }
    }

    @Test
    void theFailureToBuildTheTableOfATargetIsAnsweredByTheGroupOfTheLocator() {
        IllegalStateException failure = new IllegalStateException("no table");
        LocatedRoutes<?> broken = TestLocatedRoutes.of(located -> {
            throw failure;
        });
        Router router = router(routes -> routes.path("/shop", shop -> {
            shop.locate("/sync/{id}", (request, pathVariables) -> "order", target -> broken);
            shop.locateAsync("/async/{id}", (request, pathVariables) -> CompletableFuture.completedFuture("order"), target -> broken);
            shop.error(IllegalStateException.class, (request, error) -> HttpResponse.ok());
        }));

        for (String path : List.of("/shop/sync/1/items", "/shop/async/1/items")) {
            HttpRequest<?> request = HttpRequest.GET(path);
            Throwable thrown = assertThrows(RuntimeException.class, () -> router.findClosest(request));
            assertNotNull(GroupErrorRoutes.findErrorRoute(request, null, thrown), path);
        }
    }

    @Test
    void theStatusOfAFailedLocatorIsAnsweredByTheStatusRouteOfItsGroup() {
        HttpStatusException failure = new HttpStatusException(HttpStatus.NOT_FOUND, "no order");
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.path("/shop", shop -> {
            shop.locate("/sync/{id}", (request, pathVariables) -> {
                throw failure;
            }, target -> items);
            shop.locateAsync("/async/{id}", (request, pathVariables) -> CompletableFuture.failedFuture(failure), target -> items);
            shop.status(HttpStatus.NOT_FOUND, request -> HttpResponse.ok());
        }));

        for (String path : List.of("/shop/sync/1/items", "/shop/async/1/items")) {
            HttpRequest<?> request = HttpRequest.GET(path);
            assertSame(failure, assertThrows(HttpStatusException.class, () -> router.findClosest(request)));
            assertNotNull(GroupErrorRoutes.findStatusRoute(request, null, HttpStatus.NOT_FOUND.getCode(), failure), path);
            assertNull(GroupErrorRoutes.findStatusRoute(request, null, HttpStatus.NOT_FOUND.getCode(),
                new HttpStatusException(HttpStatus.NOT_FOUND, "another")), path);
        }
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
