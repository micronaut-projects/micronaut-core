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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The statistics of a Micronaut template that resolve the ambiguous requests are read once, when
 * the route is built, and equal the ones the template computes.
 */
class RouteAmbiguityStatisticsTest {

    private static final List<String> TEMPLATES = List.of(
        "/items",
        "/items/latest/",
        "/items/{id}",
        "/items/{id}/parts/{part}",
        "/items/{id:\\d+}",
        "/items/{id}{/optional}",
        "/items/{id}{?q,limit}",
        "/items/{id}{#section}",
        "/files/{+path}",
        "/{a}/{b}/{c}"
    );

    @Test
    void theCachedStatisticsEqualTheOnesOfTheTemplate() {
        for (String template : TEMPLATES) {
            for (String mounted : List.of("", "/api")) {
                check(template, mounted);
            }
        }
    }

    private static void check(String template, String mounted) {
        {
            Router router = router(routes -> {
                if (mounted.isEmpty()) {
                    routes.GET(template, (request, pathVariables) -> HttpResponse.ok());
                } else {
                    routes.path(mounted, group -> group.GET(template, (request, pathVariables) -> HttpResponse.ok()));
                }
            });
            DefaultUrlRouteInfo<?, ?> route = (DefaultUrlRouteInfo<?, ?>) router.uriRoutes().findFirst().orElseThrow();
            UriMatchTemplate uriMatchTemplate = route.getUriMatchTemplate();
            String described = mounted + template;
            assertEquals(uriMatchTemplate.getPathVariableSegmentCount(), route.getAmbiguityVariableSegments(), described);
            assertEquals(uriMatchTemplate.getRawSegmentLength(), route.getAmbiguityRawSegmentLength(), described);
        }
    }

    @Test
    void theRouteWithTheFewestVariablesAndTheLongestLiteralsWins() {
        Router router = router(routes -> {
            routes.GET("/items/{id}", (request, pathVariables) -> HttpResponse.ok("variable"));
            routes.GET("/items/latest", (request, pathVariables) -> HttpResponse.ok("literal"));
            routes.GET("/{kind}/{id}", (request, pathVariables) -> HttpResponse.ok("broad"));
        });
        assertEquals("/items/latest", closest(router, HttpRequest.GET("/items/latest")));
        assertEquals("/items/{id}", closest(router, HttpRequest.GET("/items/1")));
        assertEquals("/{kind}/{id}", closest(router, HttpRequest.GET("/parts/1")));
    }

    @Test
    void theMediaTypesNarrowBeforeTheTemplates() {
        Router router = router(routes -> {
            routes.GET("/items/latest").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, pathVariables) -> HttpResponse.ok("text"));
            routes.GET("/items/{id}").produces(MediaType.APPLICATION_JSON_TYPE).handle((request, pathVariables) -> HttpResponse.ok("json"));
        });
        assertEquals("/items/{id}", closest(router, HttpRequest.GET("/items/latest").accept(MediaType.APPLICATION_JSON_TYPE)));
        assertEquals("/items/latest", closest(router, HttpRequest.GET("/items/latest").accept(MediaType.TEXT_PLAIN_TYPE)));
    }

    @Test
    void equallyCloseRoutesStayAmbiguous() {
        Router router = router(routes -> {
            routes.GET("/items/{id}", (request, pathVariables) -> HttpResponse.ok("a"));
            routes.GET("/items/{key}", (request, pathVariables) -> HttpResponse.ok("b"));
        });
        assertEquals(2, router.findAllClosest(HttpRequest.GET("/items/1")).size());
    }

    @Test
    void mediaPreferencePreservesInputAndOrderForAllNoneAndSomeExplicitCandidates() {
        for (int count : List.of(0, 1, 2, 8, 32)) {
            for (int preference : List.of(0, 1, 2, 3)) {
                Router router = router(routes -> {
                    for (int i = 0; i < count; i++) {
                        boolean explicit = preference == 1 || preference == 2 && i % 2 == 0 || preference == 3 && i % 2 != 0;
                        var route = routes.POST("/items/{id" + i + "}");
                        if (explicit) {
                            route.produces(MediaType.TEXT_PLAIN_TYPE).consumes(MediaType.TEXT_PLAIN_TYPE);
                        } else {
                            route.produces(MediaType.ALL_TYPE).consumesAll();
                        }
                        route.handle((request, variables) -> HttpResponse.ok());
                    }
                });
                List<UriRouteMatch<Object, Object>> input = List.copyOf(router.<Object, Object>find(HttpMethod.POST, "/items/1", null).toList());
                var request = HttpRequest.POST("/items/1", "body").contentType(MediaType.TEXT_PLAIN_TYPE).accept(MediaType.TEXT_PLAIN_TYPE);
                var expected = new ArrayList<UriRouteMatch<Object, Object>>();
                for (var match : input) {
                    if (match.getRouteInfo().explicitlyProduces(MediaType.TEXT_PLAIN_TYPE)) {
                        expected.add(match);
                    }
                }
                if (expected.isEmpty()) {
                    expected.addAll(input);
                }
                for (int repeat = 0; repeat < 2; repeat++) {
                    assertEquals(expected, DefaultRouter.resolveAmbiguity(request, input));
                    assertEquals(count, input.size());
                }
            }
        }
    }

    @Test
    void specificityNarrowingDoesNotModifyTheCandidateList() {
        Router router = router(routes -> {
            routes.GET("/items/latest").handle((request, variables) -> HttpResponse.ok());
            routes.GET("/items/{id}").handle((request, variables) -> HttpResponse.ok());
        });
        var input = router.<Object, Object>find(HttpMethod.GET, "/items/latest", null).toList();
        var expected = List.of(input.get(0));
        assertEquals(expected, DefaultRouter.resolveAmbiguity(HttpRequest.GET("/items/latest"), input));
        assertEquals(expected, DefaultRouter.resolveAmbiguity(HttpRequest.GET("/items/latest"), input));
        assertEquals(2, input.size());
    }

    private static String closest(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        return match.getRouteInfo().getUriMatchTemplate().toPathString();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        routes.accept(builder);
        builder.close();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
