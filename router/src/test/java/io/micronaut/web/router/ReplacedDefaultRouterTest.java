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
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A router that replaces {@link DefaultRouter} and calls the constructor with the route builders
 * only, as before the {@link HttpRoutes} beans existed, still has their routes: they reach the
 * router as a route builder bean.
 */
class ReplacedDefaultRouterTest {

    static final String SPEC_NAME = "ReplacedDefaultRouterTest";

    @Test
    void aReplacedDefaultRouterWithTheRouteBuildersOnlyRoutesTheHttpRoutes() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            Router router = context.getBean(Router.class);
            assertInstanceOf(CustomRouter.class, router);

            HttpRequest<?> request = HttpRequest.GET("/functional/hello");
            UriRouteMatch<Object, Object> match = router.findClosest(request);
            assertNotNull(match, "the functional route is routed");
            assertEquals("/functional/hello", match.getRouteInfo().getUriMatchTemplate().toString());

            // declared once, with its implicit HEAD route
            assertEquals(1, router.uriRoutes()
                .filter(route -> route.getHttpMethod() == HttpMethod.GET && route.getUriMatchTemplate().toString().equals("/functional/hello"))
                .count());
            assertNotNull(router.findClosest(HttpRequest.HEAD("/functional/hello")));
            // the status routes of the HttpRoutes beans too
            assertTrue(router.findStatusRoute(HttpStatus.I_AM_A_TEAPOT, request).isPresent());
        }
    }

    @Singleton
    @Replaces(DefaultRouter.class)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CustomRouter extends DefaultRouter {

        @Inject
        CustomRouter(Collection<RouteBuilder> builders) {
            super(builders);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FunctionalRoutes implements HttpRoutes {

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/functional/hello", (request, pathVariables) -> HttpResponse.ok("hello"));
            routes.status(HttpStatus.I_AM_A_TEAPOT, request -> HttpResponse.ok("teapot"));
        }
    }
}
