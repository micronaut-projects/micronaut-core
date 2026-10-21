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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.web.router.builder.ContextRouteRequestFilter;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.RequestHandler;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route infos of a router do not keep the assembly that built them, nor its routes,
 * reachable: e.g. the executor of a route info holds the executor selector and the choice of
 * the route, not the route.
 */
class RouteAssemblyRetentionTest {

    @Test
    void theRouteInfosDoNotKeepTheAssemblyReachable() throws InterruptedException {
        @SuppressWarnings("unchecked")
        WeakReference<RouteAssembly>[] assembly = new WeakReference[1];
        Router router = router(assembly, routes -> {
            routes.GET("/plain").handle(handler());
            routes.GET("/executor").executeOn("route-executor").handle(handler());
            HttpRouteSpec filtered = routes.GET("/filtered");
            filtered.before((ContextRouteRequestFilter) (request, context) -> { }).executeOn("filter-executor");
            filtered.handle(handler());
            routes.path("/group", group -> {
                group.executeOn("group-executor");
                group.GET("/route").handle(handler());
            });
        });

        awaitCollected(assembly[0]);

        // the route infos still select with the choice of their routes
        assertNotNull(route(router, "/plain"));
        Exception routeError = assertThrows(Exception.class, () -> route(router, "/executor").getExecutor(ThreadSelection.MANUAL));
        assertTrue(routeError.getMessage().contains("route-executor"), routeError.getMessage());
        Exception groupError = assertThrows(Exception.class, () -> route(router, "/group/route").getExecutor(ThreadSelection.MANUAL));
        assertTrue(groupError.getMessage().contains("group-executor"), groupError.getMessage());
        assertEquals(1, ((DefaultUrlRouteInfo<Object, Object>) route(router, "/filtered")).routeFilters.size());
    }

    private static RequestHandler handler() {
        return (request, pathVariables) -> HttpResponse.ok();
    }

    private static UriRouteInfo<Object, Object> route(Router router, String uri) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(uri));
        assertNotNull(match, uri);
        return match.getRouteInfo();
    }

    private static void awaitCollected(WeakReference<?> reference) throws InterruptedException {
        for (int i = 0; i < 50 && reference.get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull(reference.get(), "the assembly is reachable from the router");
    }

    /**
     * Builds the router in a frame of its own, so only the router stays reachable.
     */
    private static Router router(WeakReference<RouteAssembly>[] assemblyRef, Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        DefaultRouter router = new DefaultRouter(List.of(), List.of(() -> assembly));
        assemblyRef[0] = new WeakReference<>(assembly);
        return router;
    }
}
