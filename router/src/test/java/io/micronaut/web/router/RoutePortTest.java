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
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.RouteDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The port of a handler route and of a group of handler routes: like {@code @Controller(port)},
 * the port is exposed, and the route matches the requests on that port only.
 */
class RoutePortTest {

    private static final int DEFAULT_PORT = 8080;
    private static final int MANAGEMENT_PORT = 9090;
    private static final int OTHER_PORT = 9191;

    @Test
    void aRouteWithAPortMatchesTheRequestsOnThatPortOnly() {
        Router router = router(routes -> {
            routes.GET("/metrics", RoutePortTest::ok).port(MANAGEMENT_PORT);
            routes.GET("/orders", RoutePortTest::ok);
        });

        assertEquals(Set.of(MANAGEMENT_PORT), router.getExposedPorts());
        router.applyDefaultPorts(List.of(DEFAULT_PORT));
        assertNotNull(router.findClosest(onPort(HttpRequest.GET("/metrics"), MANAGEMENT_PORT)));
        assertNull(router.findClosest(onPort(HttpRequest.GET("/metrics"), DEFAULT_PORT)));
        // a route without a port matches the requests on the default ports only
        assertNotNull(router.findClosest(onPort(HttpRequest.GET("/orders"), DEFAULT_PORT)));
        assertNull(router.findClosest(onPort(HttpRequest.GET("/orders"), MANAGEMENT_PORT)));
        // the implicit HEAD route has the port of its GET route
        assertNotNull(router.findClosest(onPort(HttpRequest.HEAD("/metrics"), MANAGEMENT_PORT)));
        assertNull(router.findClosest(onPort(HttpRequest.HEAD("/metrics"), DEFAULT_PORT)));
        // not a 405 on another port: the route does not exist there
        assertTrue(router.findAny(onPort(HttpRequest.POST("/metrics", ""), DEFAULT_PORT)).isEmpty());
        UriRouteMatch<Object, Object> match = router.findClosest(onPort(HttpRequest.GET("/metrics"), MANAGEMENT_PORT));
        assertNotNull(match);
        assertEquals(MANAGEMENT_PORT, ((UriRouteInfo<?, ?>) match.getRouteInfo()).getPort());
    }

    @Test
    void theRoutesOfAGroupInheritItsPortWhereverItIsDeclaredAndARouteOverridesIt() {
        Router router = router(routes -> routes.path("/management", management -> {
            management.GET("/health", RoutePortTest::ok);
            management.path("/nested", nested -> nested.GET("/info", RoutePortTest::ok));
            management.GET("/other", RoutePortTest::ok).port(OTHER_PORT);
            management.path("/own", own -> {
                own.port(OTHER_PORT);
                own.GET("/route", RoutePortTest::ok);
            });
            // declared after the routes
            management.port(MANAGEMENT_PORT);
        }));

        assertEquals(Set.of(MANAGEMENT_PORT, OTHER_PORT), router.getExposedPorts());
        router.applyDefaultPorts(List.of(DEFAULT_PORT));
        for (String path : List.of("/management/health", "/management/nested/info")) {
            assertNotNull(router.findClosest(onPort(HttpRequest.GET(path), MANAGEMENT_PORT)), path);
            assertNull(router.findClosest(onPort(HttpRequest.GET(path), DEFAULT_PORT)), path);
            assertNull(router.findClosest(onPort(HttpRequest.GET(path), OTHER_PORT)), path);
        }
        for (String path : List.of("/management/other", "/management/own/route")) {
            assertNotNull(router.findClosest(onPort(HttpRequest.GET(path), OTHER_PORT)), path);
            assertNull(router.findClosest(onPort(HttpRequest.GET(path), MANAGEMENT_PORT)), path);
        }
    }

    @Test
    void theSameTemplateOnTwoPortsIsTwoRoutes() {
        Router router = router(routes -> {
            routes.GET("/status", (request, pathVariables) -> HttpResponse.ok("default"));
            routes.GET("/status", (request, pathVariables) -> HttpResponse.ok("management")).port(MANAGEMENT_PORT);
        });
        router.applyDefaultPorts(List.of(DEFAULT_PORT));

        UriRouteMatch<Object, Object> management = router.findClosest(onPort(HttpRequest.GET("/status"), MANAGEMENT_PORT));
        assertNotNull(management);
        assertEquals(MANAGEMENT_PORT, ((UriRouteInfo<?, ?>) management.getRouteInfo()).getPort());
        UriRouteMatch<Object, Object> defaultMatch = router.findClosest(onPort(HttpRequest.GET("/status"), DEFAULT_PORT));
        assertNotNull(defaultMatch);
        assertNull(((UriRouteInfo<?, ?>) defaultMatch.getRouteInfo()).getPort());
    }

    @Test
    void aDeclaredRouteExposesItsPortWhenItIsDeclared() {
        RouteDeclaration declaration = RouteDeclaration.of(HttpMethod.GET, "/declared/{id}");
        Router router = router(routes -> routes.handle(declaration, RoutePortTest::ok).port(MANAGEMENT_PORT));

        // before the route is built
        assertEquals(Set.of(MANAGEMENT_PORT), router.getExposedPorts());
        router.applyDefaultPorts(List.of(DEFAULT_PORT));
        assertNotNull(router.findClosest(onPort(HttpRequest.GET("/declared/5"), MANAGEMENT_PORT)));
        assertNull(router.findClosest(onPort(HttpRequest.GET("/declared/5"), DEFAULT_PORT)));
    }

    @Test
    void theExposedPortsOfAnAssemblyAreReadOnly() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        new DefaultHttpRouteBuilder(assembly).GET("/metrics", RoutePortTest::ok).port(MANAGEMENT_PORT);

        assertEquals(Set.of(MANAGEMENT_PORT), assembly.exposedPorts());
        assertThrows(UnsupportedOperationException.class, () -> assembly.exposedPorts().add(OTHER_PORT));
        assertThrows(UnsupportedOperationException.class, () -> assembly.exposedPorts().clear());
    }

    @Test
    void aPortTheServerCannotListenOnIsRejected() {
        RouteDeclaration declaration = RouteDeclaration.of(HttpMethod.GET, "/declared/{id}");
        Router router = router(routes -> {
            for (int port : new int[] {-1, 0, 65_536, Integer.MIN_VALUE}) {
                assertInvalidPort(port, () -> routes.GET("/metrics", RoutePortTest::ok).port(port));
                assertInvalidPort(port, () -> routes.handle(Set.of(HttpMethod.GET, HttpMethod.POST), "/multi", RoutePortTest::ok).port(port));
                assertInvalidPort(port, () -> routes.handle(declaration, RoutePortTest::ok).port(port));
                routes.group(group -> assertInvalidPort(port, () -> group.port(port)));
            }
            routes.GET("/lowest", RoutePortTest::ok).port(1);
            routes.GET("/highest", RoutePortTest::ok).port(65_535);
        });

        // nothing was exposed by a rejected port
        assertEquals(Set.of(1, 65_535), router.getExposedPorts());
    }

    private static void assertInvalidPort(int port, Executable call) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);
        assertEquals("The port of a route must be between 1 and 65535: " + port, e.getMessage());
    }

    @SuppressWarnings("unchecked")
    static HttpRequest<?> onPort(HttpRequest<?> request, int port) {
        InetSocketAddress address = new InetSocketAddress("localhost", port);
        return new HttpRequestWrapper<Object>((HttpRequest<Object>) request) {
            @Override
            public InetSocketAddress getServerAddress() {
                return address;
            }
        };
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
