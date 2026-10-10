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
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RequestHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The {@code HEAD} and {@code OPTIONS} creators of the route builder and of its groups, like
 * {@code @Head} and {@code @Options} controller methods.
 */
class HeadAndOptionsRoutesTest {

    @Test
    void aHeadRouteReplacesTheImplicitHeadRouteOfTheGetRoute() {
        Router router = router(routes -> {
            routes.GET("/items", handler("get"));
            routes.HEAD("/items", handler("head"));
        });

        assertEquals("head", target(router, HttpRequest.HEAD("/items")));
        assertEquals("get", target(router, HttpRequest.GET("/items")));
        assertEquals(1, router.uriRoutes().filter(route -> route.getHttpMethod() == HttpMethod.HEAD).count(),
            "no implicit HEAD route next to the declared one");
    }

    @Test
    void aHeadRouteIsConfiguredLikeAnyRoute() {
        Router router = router(routes -> routes.HEAD("/probe/{name}")
            .constrain("name", List.of("live"))
            .handle(handler("live")));

        assertEquals("live", target(router, HttpRequest.HEAD("/probe/live")));
        assertNull(router.findClosest(HttpRequest.HEAD("/probe/ready")));
        assertNull(router.findClosest(HttpRequest.GET("/probe/live")), "a HEAD route answers HEAD requests only");
    }

    @Test
    void anOptionsRouteAnswersTheOptionsRequestsOfItsUri() {
        Router router = router(routes -> {
            routes.GET("/items", handler("get"));
            routes.OPTIONS("/items", handler("options"));
            routes.OPTIONS("/reports/{id}").order(-1).handle(handler("reports"));
        });

        assertEquals("options", target(router, HttpRequest.OPTIONS("/items")));
        assertEquals("reports", target(router, HttpRequest.OPTIONS("/reports/1")));
        assertEquals("get", target(router, HttpRequest.GET("/items")));
    }

    @Test
    void theCreatorsOfAGroupDeclareHeadAndOptionsRoutes() {
        Router router = router(routes -> routes.path("/group", group -> {
            group.HEAD("/head", handler("group head"));
            group.OPTIONS("/options", handler("group options"));
            group.HEAD("/configured").order(1).handle(handler("configured head"));
            group.OPTIONS("/configured").order(1).handle(handler("configured options"));
        }));

        assertEquals("group head", target(router, HttpRequest.HEAD("/group/head")));
        assertEquals("group options", target(router, HttpRequest.OPTIONS("/group/options")));
        assertEquals("configured head", target(router, HttpRequest.HEAD("/group/configured")));
        assertEquals("configured options", target(router, HttpRequest.OPTIONS("/group/configured")));
    }

    private static String target(Router router, MutableHttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getMethodName() + " " + request.getPath());
        return ((Named) ((HandlerMethod<?>) match.getRouteInfo().getTargetMethod()).getTarget()).name();
    }

    private static RequestHandler handler(String name) {
        return new Named(name);
    }

    private record Named(String name) implements RequestHandler {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) {
            return HttpResponse.ok(name);
        }
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
