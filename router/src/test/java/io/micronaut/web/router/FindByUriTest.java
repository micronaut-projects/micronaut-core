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
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Router#find(HttpRequest, CharSequence)}: the routes that match the given URI, not the
 * path of the request.
 */
class FindByUriTest {

    @Test
    void theGivenUriSelectsTheRoutes() {
        Router router = router(routes -> {
            routes.GET("/target").handle((request, variables) -> HttpResponse.ok());
            routes.GET("/source").handle((request, variables) -> HttpResponse.ok());
        });

        List<String> found = router.find(HttpRequest.GET("/source"), "/target")
            .map(match -> match.getRouteInfo().getUriMatchTemplate().toString())
            .toList();
        assertEquals(List.of("/target"), found);
    }

    @Test
    void theConstraintsCheckTheVariablesOfTheGivenUri() {
        Router router = router(routes -> routes.GET("/orders/{id}")
            .constrain("id", Long.class, id -> id > 0)
            .handle((request, variables) -> HttpResponse.ok()));

        assertEquals(0, router.find(HttpRequest.GET("/orders/5"), "/orders/-5").count(),
            "the variables of the given URI violate the constraint");
        assertEquals(1, router.find(HttpRequest.GET("/orders/-5"), "/orders/5").count(),
            "the variables of the given URI meet the constraint");
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
