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
import io.micronaut.web.router.builder.RequestCondition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The ranks in which the routes of a body-conditioned request are tried.
 */
class BodyFallThroughRankTest {

    @Test
    void oneRouteIsItsOwnRankAndTheRanksDoNotFollowTheList() {
        Router router = router(routes -> routes.POST("/items")
            .where(RequestCondition.bodyText(text -> text.contains("express")))
            .handle((request, pathVariables) -> HttpResponse.ok()));
        HttpRequest<?> request = HttpRequest.POST("/items", "express");
        List<UriRouteMatch<Object, Object>> matches = new ArrayList<>(router.findAny(request));
        assertEquals(1, matches.size());
        UriRouteMatch<Object, Object> only = matches.get(0);

        List<List<UriRouteMatch<Object, Object>>> ranks = BodyFallThrough.ACROSS_SPECIFICITY.rank(request, matches);
        matches.clear();

        assertEquals(1, ranks.size());
        assertEquals(1, ranks.get(0).size());
        assertSame(only, ranks.get(0).get(0));
    }

    @Test
    void equallyCloseConditionedRoutesShareARank() {
        Router router = router(routes -> {
            routes.POST("/items").where(RequestCondition.bodyText(text -> text.contains("a"))).handle((request, pathVariables) -> HttpResponse.ok());
            routes.POST("/items").where(RequestCondition.bodyText(text -> text.contains("b"))).handle((request, pathVariables) -> HttpResponse.ok());
        });
        HttpRequest<?> request = HttpRequest.POST("/items", "ab");
        List<List<UriRouteMatch<Object, Object>>> ranks = BodyFallThrough.ACROSS_SPECIFICITY.rank(request, router.findAny(request));
        assertEquals(1, ranks.size());
        assertEquals(2, ranks.get(0).size());
    }

    @Test
    void aSpecificConditionedRouteFallsThroughToALessSpecificOne() {
        Router router = router(routes -> {
            routes.POST("/items/latest").where(RequestCondition.bodyText(text -> text.contains("x"))).handle((request, pathVariables) -> HttpResponse.ok());
            routes.POST("/items/{id}", (request, pathVariables) -> HttpResponse.ok());
        });
        HttpRequest<?> request = HttpRequest.POST("/items/latest", "y");
        List<List<UriRouteMatch<Object, Object>>> ranks = BodyFallThrough.ACROSS_SPECIFICITY.rank(request, router.findAny(request));
        assertEquals(2, ranks.size());
        assertEquals("/items/latest", ranks.get(0).get(0).getRouteInfo().getUriMatchTemplate().toPathString());
        assertEquals("/items/{id}", ranks.get(1).get(0).getRouteInfo().getUriMatchTemplate().toPathString());
    }

    @Test
    void aSpecificMethodRanksBeforeAnyMethod() {
        Router router = router(routes -> {
            routes.any("/items").where(RequestCondition.bodyText(text -> text.contains("x"))).handle((request, pathVariables) -> HttpResponse.ok());
            routes.POST("/items").where(RequestCondition.bodyText(text -> text.contains("x"))).handle((request, pathVariables) -> HttpResponse.ok());
        });
        HttpRequest<?> request = HttpRequest.POST("/items", "x");
        List<List<UriRouteMatch<Object, Object>>> ranks = BodyFallThrough.ACROSS_SPECIFICITY.rank(request, router.findAny(request));
        assertEquals(2, ranks.size());
        assertEquals(1, ranks.get(0).size());
        assertEquals("POST", ranks.get(0).get(0).getRouteInfo().getHttpMethodName());
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        routes.accept(builder);
        builder.close();
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
