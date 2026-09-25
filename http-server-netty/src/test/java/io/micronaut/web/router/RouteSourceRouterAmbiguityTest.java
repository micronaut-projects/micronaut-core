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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Route match filters, such as versioning, apply to the candidates of a tier before its ambiguity
 * is resolved, and the tiers keep their precedence, whether a {@link FilteredRouter} decorates the
 * router or not: the {@link FilteredRouter} lets the {@link DefaultRouter} resolve the ambiguity
 * tier by tier instead of resolving it across the tiers.
 */
class RouteSourceRouterAmbiguityTest {
    private static final String SPEC_NAME = "RouteSourceRouterAmbiguityTest";
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void anApplicationRouteTakesPrecedenceOverAMoreSpecificRouteOfASource(boolean filteredRouterOutside) {
        Router router = router(filteredRouterOutside);

        assertEquals("tiered", closestMethod(router, HttpRequest.GET("/tiered/special")));
        assertEquals("tiered", closestMethod(router, HttpRequest.GET("/tiered/other")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void theSameTemplateInTwoTiersIsNoDuplicate(boolean filteredRouterOutside) {
        Router router = router(filteredRouterOutside);

        assertEquals("same", closestMethod(router, HttpRequest.GET("/same/x")));
        assertEquals(1, router.findAllClosest(HttpRequest.GET("/same/x")).size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aMoreSpecificRouteOfAnotherVersionDoesNotHideARouteOfTheApplication(boolean filteredRouterOutside) {
        Router router = router(filteredRouterOutside);

        assertEquals("v1", closestMethod(router, HttpRequest.GET("/items/special").header(VERSION_HEADER, "1")));
        assertEquals("v2Special", closestMethod(router, HttpRequest.GET("/items/special").header(VERSION_HEADER, "2")));
        assertEquals("v1", closestMethod(router, HttpRequest.GET("/items/other").header(VERSION_HEADER, "1")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aMoreSpecificRouteOfAnotherVersionDoesNotHideARouteOfASource(boolean filteredRouterOutside) {
        Router router = router(filteredRouterOutside);

        assertEquals("handleV1", closestMethod(router, HttpRequest.GET("/table-items/special").header(VERSION_HEADER, "1")));
        assertEquals("handleV2", closestMethod(router, HttpRequest.GET("/table-items/special").header(VERSION_HEADER, "2")));
        assertEquals("handleV1", closestMethod(router, HttpRequest.GET("/table-items/other").header(VERSION_HEADER, "1")));
    }

    @Test
    void theApplicationRouterResolvesTheAmbiguityTierByTier() {
        try (EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
             HttpClient httpClient = context.createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient client = httpClient.toBlocking();

            assertEquals("controller special", client.retrieve(HttpRequest.GET("/tiered/special")));
            assertEquals("controller x", client.retrieve(HttpRequest.GET("/same/x")));
            assertEquals("v1 special", client.retrieve(HttpRequest.GET("/items/special").header(VERSION_HEADER, "1")));
            assertEquals("v1 /table-items/special", client.retrieve(HttpRequest.GET("/table-items/special").header(VERSION_HEADER, "1")));
            assertEquals("v2 /table-items/special", client.retrieve(HttpRequest.GET("/table-items/special").header(VERSION_HEADER, "2")));
        }
    }

    private Router router(boolean filteredRouterOutside) {
        VersionRouteMatchFilter versionFilter = context.getBean(VersionRouteMatchFilter.class);
        List<RouteMatchFilter> filters = List.of(versionFilter);
        List<RouteSource> sources = List.of(context.getBean(Routes.class));
        Router tiered = DefaultRouter.withRouteSources(context.getBeansOfType(RouteBuilder.class), () -> sources, () -> filters);
        return filteredRouterOutside ? new FilteredRouter(tiered, versionFilter) : tiered;
    }

    private static String closestMethod(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        return match.getRouteInfo().getTargetMethod().getMethodName();
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TieredController {
        @Get(value = "/tiered/{name}", produces = MediaType.TEXT_PLAIN)
        String tiered(String name) {
            return "controller " + name;
        }

        @Get(value = "/same/{name}", produces = MediaType.TEXT_PLAIN)
        String same(String name) {
            return "controller " + name;
        }

        @Version("1")
        @Get(value = "/items/{name}", produces = MediaType.TEXT_PLAIN)
        String v1(String name) {
            return "v1 " + name;
        }

        @Version("2")
        @Get(value = "/items/special", produces = MediaType.TEXT_PLAIN)
        String v2Special() {
            return "v2 special";
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Handler {
        @Executable
        HttpResponse<String> handle(HttpRequest<?> request) {
            return HttpResponse.ok("source " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Executable
        @Version("1")
        HttpResponse<String> handleV1(HttpRequest<?> request) {
            return HttpResponse.ok("v1 " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Executable
        @Version("2")
        HttpResponse<String> handleV2(HttpRequest<?> request) {
            return HttpResponse.ok("v2 " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements RouteSource {
        private final RouteTable table;

        Routes(RouteTableFactory tables) {
            table = tables.build(routes -> {
                // more specific than the application routes, but a later tier
                routes.GET("/tiered/special", Handler.class, "handle", HttpRequest.class);
                routes.GET("/same/{name}", Handler.class, "handle", HttpRequest.class);
                routes.GET("/table-items/{name}", Handler.class, "handleV1", HttpRequest.class);
                routes.GET("/table-items/special", Handler.class, "handleV2", HttpRequest.class);
            });
        }

        @Override
        public RouteTable snapshot() {
            return table;
        }
    }
}
