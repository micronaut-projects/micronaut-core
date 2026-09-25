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
import io.micronaut.http.HttpStatus;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.ErrorRouteHandler;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.StatusRouteHandler;
import io.micronaut.web.router.exceptions.RoutingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The error and status routes of groups of handler routes are local to the routes of the group:
 * the innermost group first, then the groups around it, the closest exception type within a
 * group.
 */
class GroupErrorRoutesTest {

    @Test
    void theInnermostGroupAnswersFirstThenTheGroupsAroundIt() {
        Router router = router(routes -> routes.path("/api", api -> {
            api.error(IllegalStateException.class, new NamedError<>("outer exact"));
            api.error(IllegalArgumentException.class, new NamedError<>("outer argument"));
            api.GET("/outer", GroupErrorRoutesTest::ok);
            api.path("/inner", inner -> {
                inner.GET("/route", GroupErrorRoutesTest::ok);
                // a supertype: the innermost group wins over a closer type of an outer group
                inner.error(RuntimeException.class, new NamedError<>("inner runtime"));
                inner.error(UnsupportedOperationException.class, new NamedError<>("inner unsupported"));
            });
        }));

        assertEquals("inner runtime", error(router, "/api/inner/route", new IllegalStateException()));
        assertEquals("inner unsupported", error(router, "/api/inner/route", new UnsupportedOperationException()));
        assertEquals("outer exact", error(router, "/api/outer", new IllegalStateException()));
        assertEquals("outer argument", error(router, "/api/outer", new NumberFormatException()));
        assertNull(error(router, "/api/outer", new UnsupportedOperationException()));
        // not global
        assertTrue(router.findErrorRoute(new IllegalStateException(), HttpRequest.GET("/api/outer")).isEmpty());
    }

    @Test
    void theStatusRoutesOfAGroupAreLocalToItsRoutes() {
        Router router = router(routes -> {
            routes.path("/api", api -> {
                api.GET("/items", GroupErrorRoutesTest::ok);
                api.status(HttpStatus.NOT_FOUND, new NamedStatus("api not found"));
                api.path("/inner", inner -> {
                    inner.status(HttpStatus.NOT_FOUND, new NamedStatus("inner not found"));
                    inner.GET("/items", GroupErrorRoutesTest::ok);
                });
            });
            routes.GET("/outside", GroupErrorRoutesTest::ok);
        });

        assertEquals("api not found", status(router, "/api/items", 404));
        assertEquals("inner not found", status(router, "/api/inner/items", 404));
        assertNull(status(router, "/api/items", 409));
        assertNull(status(router, "/outside", 404));
    }

    @Test
    void aDuplicateErrorRouteOfAGroupFailsWhenTheRouterIsBuilt() {
        RoutingException error = assertThrows(RoutingException.class, () -> router(routes -> routes.group(group -> {
            group.GET("/x", GroupErrorRoutesTest::ok);
            group.error(IllegalStateException.class, new NamedError<>("a"));
            group.error(IllegalStateException.class, new NamedError<>("b"));
        })));
        assertTrue(error.getMessage().contains("IllegalStateException"), error.getMessage());
        assertThrows(RoutingException.class, () -> router(routes -> routes.group(group -> {
            group.GET("/x", GroupErrorRoutesTest::ok);
            group.status(HttpStatus.NOT_FOUND, new NamedStatus("a"));
            group.status(HttpStatus.NOT_FOUND, new NamedStatus("b"));
        })));
        // the same exception type in two groups is fine
        assertNotNull(router(routes -> {
            routes.group(group -> {
                group.GET("/a", GroupErrorRoutesTest::ok);
                group.error(IllegalStateException.class, new NamedError<>("a"));
            });
            routes.group(group -> {
                group.GET("/b", GroupErrorRoutesTest::ok);
                group.error(IllegalStateException.class, new NamedError<>("b"));
            });
        }));
    }

    private static String error(Router router, String path, Throwable error) {
        HttpRequest<?> request = HttpRequest.GET(path);
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, path);
        RouteAttributes.setRouteMatch(request, match);
        RouteMatch<Object> errorRoute = GroupErrorRoutes.findErrorRoute(request, match.getRouteInfo(), error);
        return errorRoute == null ? null : name(errorRoute);
    }

    private static String status(Router router, String path, int status) {
        HttpRequest<?> request = HttpRequest.GET(path);
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, path);
        RouteAttributes.setRouteMatch(request, match);
        RouteMatch<Object> statusRoute = GroupErrorRoutes.findStatusRoute(request, match.getRouteInfo(), status);
        return statusRoute == null ? null : name(statusRoute);
    }

    private static String name(RouteMatch<?> match) {
        Object target = ((HandlerMethod<?>) ((MethodBasedRouteInfo<?, ?>) match.getRouteInfo()).getTargetMethod()).getTarget();
        return target instanceof NamedError<?> named ? named.name() : ((NamedStatus) target).name();
    }

    private record NamedError<E extends Throwable>(String name) implements ErrorRouteHandler<E> {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, E error) {
            return HttpResponse.ok(name);
        }
    }

    private record NamedStatus(String name) implements StatusRouteHandler {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request) {
            return HttpResponse.ok(name);
        }
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
