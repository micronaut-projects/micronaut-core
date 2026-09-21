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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.filter.FilteredRouter;
import io.micronaut.web.router.filter.RouteMatchFilter;
import io.micronaut.web.router.version.VersionRouteMatchFilter;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the route tables of {@link RouteSource}s compose with the application routes: filters such
 * as versioning apply to every tier before one is chosen, default ports apply to the tables, all
 * lookups see the tables, and a request keeps one snapshot of them.
 */
class RouteSourceRouterTest {
    private static final String SPEC_NAME = "RouteSourceRouterTest";
    private static final String VERSION_HEADER = "X-API-VERSION";

    private ApplicationContext context;

    @BeforeEach
    void start() {
        context = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.router.versioning.enabled", true,
            "micronaut.router.versioning.header.enabled", true
        ));
    }

    @AfterEach
    void stop() {
        context.close();
    }

    @Test
    void versionFilteredControllerFallsThroughToASource() {
        try (EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
             HttpClient httpClient = context.createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient client = httpClient.toBlocking();

            assertEquals("controller x", client.retrieve(HttpRequest.GET("/versioned/x").header(VERSION_HEADER, "1")));
            // the controller only serves version 1: the version 2 route of the source takes over
            assertEquals("v2 /versioned/x", client.retrieve(HttpRequest.GET("/versioned/x").header(VERSION_HEADER, "2")));
            assertStatus(HttpStatus.NOT_FOUND, () -> client.retrieve(HttpRequest.GET("/versioned/x").header(VERSION_HEADER, "3")));

            // the routes of a source are version filtered too
            assertEquals("v2 /v2-only/x", client.retrieve(HttpRequest.GET("/v2-only/x").header(VERSION_HEADER, "2")));
            assertStatus(HttpStatus.NOT_FOUND, () -> client.retrieve(HttpRequest.GET("/v2-only/x").header(VERSION_HEADER, "1")));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void versionFilteringDoesNotDependOnTheOrderOfTheDecorators(boolean filteredRouterOutside) {
        Router application = new DefaultRouter(context.getBeansOfType(RouteBuilder.class));
        VersionRouteMatchFilter versionFilter = context.getBean(VersionRouteMatchFilter.class);
        List<RouteMatchFilter> filters = List.of(versionFilter);
        List<RouteSource> sources = List.of(context.getBean(DynamicRoutes.class));
        Router router = filteredRouterOutside
            ? new FilteredRouter(new RouteSourceRouter(application, () -> sources, () -> filters), versionFilter)
            : new RouteSourceRouter(new FilteredRouter(application, versionFilter), () -> sources, () -> filters);

        assertEquals("v1", closestMethod(router, HttpRequest.GET("/versioned/x").header(VERSION_HEADER, "1")));
        assertEquals("v2", closestMethod(router, HttpRequest.GET("/versioned/x").header(VERSION_HEADER, "2")));
        assertNull(router.findClosest(HttpRequest.GET("/v2-only/x").header(VERSION_HEADER, "1")));
    }

    @Test
    void defaultPortsApplyToTheTables() {
        RouteSourceRouter router = new RouteSourceRouter(new DefaultRouter(List.of()), () -> List.of(context.getBean(DynamicRoutes.class)), List::of);
        router.applyDefaultPorts(List.of(8080));

        assertNotNull(router.findClosest(onPort(HttpRequest.GET("/dynamic/x"), 8080)));
        assertNull(router.findClosest(onPort(HttpRequest.GET("/dynamic/x"), 9090)));
        assertTrue(router.findAny(onPort(HttpRequest.GET("/dynamic/x"), 9090)).isEmpty());
    }

    @Test
    void everyLookupSeesTheTables() {
        Router router = context.getBean(Router.class);

        assertTrue(router.uriRoutes().anyMatch(route -> route.getUriMatchTemplate().toString().equals("/dynamic/{+path}")));
        assertTrue(router.route(io.micronaut.http.HttpMethod.GET, "/dynamic/x").isPresent());
        assertTrue(router.GET("/dynamic/x").isPresent());
        assertEquals(1, router.find(HttpRequest.GET("/dynamic/x")).count());
        assertEquals(1, router.find(io.micronaut.http.HttpMethod.GET, "/dynamic/x", null).count());
        // GET and its implicit HEAD route, like a controller GET route
        assertEquals(List.of("GET", "HEAD"), router.findAny("/dynamic/x", null).map(match -> match.getRouteInfo().getHttpMethodName()).sorted().toList());
    }

    @Test
    void aRequestKeepsItsSnapshot() {
        Router router = context.getBean(Router.class);
        DynamicRoutes routes = context.getBean(DynamicRoutes.class);
        HttpRequest<?> request = HttpRequest.GET("/snapshot/x");
        try {
            routes.replace(r -> r.GET("/snapshot/{name}", Handler.class, "handle", HttpRequest.class));
            assertNotNull(router.findClosest(request));

            routes.replace(r -> { });
            // the same request still sees the table it started with, a new one sees the new table
            assertNotNull(router.findClosest(request));
            assertEquals(1, router.findAny(request).stream().filter(match -> match.getHttpMethod() == io.micronaut.http.HttpMethod.GET).count());
            assertNull(router.findClosest(HttpRequest.GET("/snapshot/x")));
        } finally {
            routes.reset();
        }
    }

    @Test
    void tablesOnlyDeclareUriRoutes() {
        RouteTableFactory tables = context.getBean(RouteTableFactory.class);
        assertThrows(IllegalArgumentException.class, () -> tables.build(r -> r.GET("/ported", Handler.class, "handle", HttpRequest.class).exposedPort(9999)));
        assertThrows(IllegalArgumentException.class, () -> tables.build(r -> r.status(HttpStatus.NOT_FOUND, Handler.class, "handle", HttpRequest.class)));
    }

    @Test
    void getRoutesOfATableHaveAnImplicitHeadRoute() {
        RouteTableFactory tables = context.getBean(RouteTableFactory.class);
        Router router = tableRouter(tables.build(r -> r.GET("/head/{name}", Handler.class, "handle", HttpRequest.class)));

        UriRouteMatch<Object, Object> head = router.findClosest(HttpRequest.HEAD("/head/x"));
        assertNotNull(head);
        assertTrue(head.getRouteInfo().isImplicitHead());

        // an explicit HEAD route of the table takes precedence, without a duplicate route
        Router explicit = tableRouter(tables.build(r -> {
            r.GET("/head/{name}", Handler.class, "handle", HttpRequest.class);
            r.HEAD("/head/{name}", Handler.class, "handleV2", HttpRequest.class);
        }));
        assertEquals("handleV2", explicit.findClosest(HttpRequest.HEAD("/head/x")).getRouteInfo().getTargetMethod().getMethodName());
    }

    @Test
    void theMediaTypesOfTheHandlerMethodApply() {
        RouteTableFactory tables = context.getBean(RouteTableFactory.class);
        Router router = tableRouter(tables.build(r -> r.POST("/text", Handler.class, "text", String.class)));

        assertNotNull(router.findClosest(HttpRequest.POST("/text", "hello").contentType(MediaType.TEXT_PLAIN_TYPE)));
        assertNull(router.findClosest(HttpRequest.POST("/text", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)));

        // the build callback can still change them
        Router json = tableRouter(tables.build(r -> r.POST("/text", Handler.class, "text", String.class).consumes(MediaType.APPLICATION_JSON_TYPE)));
        assertNull(json.findClosest(HttpRequest.POST("/text", "hello").contentType(MediaType.TEXT_PLAIN_TYPE)));
    }

    @Test
    void aTableDoesNotChangeWithTheRoutesItWasBuiltFrom() {
        UriRoute[] retained = new UriRoute[1];
        Router router = tableRouter(context.getBean(RouteTableFactory.class).build(r -> retained[0] = r.GET("/fixed", Handler.class, "handle", HttpRequest.class)));
        assertNotNull(router.findClosest(HttpRequest.GET("/fixed")));

        retained[0].where(request -> false);

        assertNotNull(router.findClosest(HttpRequest.GET("/fixed")));
        assertNotNull(router.findClosest(HttpRequest.HEAD("/fixed")));
    }

    private static Router tableRouter(RouteTable table) {
        return new RouteSourceRouter(new DefaultRouter(List.of()), () -> List.of(() -> table), List::of);
    }

    private static String closestMethod(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        return match.getRouteInfo().getTargetMethod().getMethodName().equals("handleV2") ? "v2" : "v1";
    }

    private static HttpRequest<?> onPort(HttpRequest<Object> request, int port) {
        InetSocketAddress address = new InetSocketAddress("localhost", port);
        return new HttpRequestWrapper<>(request) {
            @Override
            public InetSocketAddress getServerAddress() {
                return address;
            }
        };
    }

    private static void assertStatus(HttpStatus status, org.junit.jupiter.api.function.Executable request) {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, request);
        assertEquals(status, e.getStatus());
    }

    @Controller("/versioned")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class VersionedController {
        @Version("1")
        @Get(value = "/{name}", produces = MediaType.TEXT_PLAIN)
        String v1(String name) {
            return "controller " + name;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Handler {
        @Executable
        HttpResponse<String> handle(HttpRequest<?> request) {
            return HttpResponse.ok("dynamic " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Executable
        @Version("2")
        HttpResponse<String> handleV2(HttpRequest<?> request) {
            return HttpResponse.ok("v2 " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Executable
        @Consumes(MediaType.TEXT_PLAIN)
        String text(@Body String body) {
            return body;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class DynamicRoutes implements RouteSource {
        private final RouteTableFactory tables;
        private volatile RouteTable current;

        DynamicRoutes(RouteTableFactory tables) {
            this.tables = tables;
            reset();
        }

        void reset() {
            replace(routes -> {
                routes.GET("/versioned/{+path}", Handler.class, "handleV2", HttpRequest.class);
                routes.GET("/v2-only/{+path}", Handler.class, "handleV2", HttpRequest.class);
                routes.GET("/dynamic/{+path}", Handler.class, "handle", HttpRequest.class);
            });
        }

        void replace(Consumer<RouteBuilder> routes) {
            current = tables.build(routes);
        }

        @Override
        public RouteTable snapshot() {
            return current;
        }
    }
}
