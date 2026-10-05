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

import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.RequestPredicates;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static io.micronaut.web.router.builder.RequestPredicates.accept;
import static io.micronaut.web.router.builder.RequestPredicates.all;
import static io.micronaut.web.router.builder.RequestPredicates.any;
import static io.micronaut.web.router.builder.RequestPredicates.contentType;
import static io.micronaut.web.router.builder.RequestPredicates.header;
import static io.micronaut.web.router.builder.RequestPredicates.method;
import static io.micronaut.web.router.builder.RequestPredicates.queryParam;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conditions of {@link RequestPredicates}.
 */
class RequestPredicatesTest {

    @Test
    void headerConditions() {
        MutableHttpRequest<?> request = HttpRequest.GET("/x").header("X-Mode", "a").header("X-Mode", "b");

        assertTrue(meets(header("x-mode"), request), "the name is case-insensitive");
        assertFalse(meets(header("X-Other"), request));
        assertTrue(meets(header("X-Mode", "b"), request), "any of the values");
        assertFalse(meets(header("X-Mode", "B"), request), "the value is case-sensitive");
        assertTrue(meets(header("X-Mode", value -> value.startsWith("a")), request));
        assertFalse(meets(header("X-Other", value -> true), request));
    }

    @Test
    void queryParameterConditions() {
        MutableHttpRequest<?> request = HttpRequest.GET("/x");
        request.getParameters().add("format", List.of("csv", "json")).add("debug", List.of(""));

        assertTrue(meets(queryParam("debug"), request));
        assertFalse(meets(queryParam("Format"), request), "the name is case-sensitive");
        assertTrue(meets(queryParam("format", "json"), request), "any of the values");
        assertFalse(meets(queryParam("format", "xml"), request));
        assertTrue(meets(queryParam("format", value -> value.length() == 3), request));
    }

    @Test
    void mediaTypeConditions() {
        assertTrue(meets(accept(MediaType.TEXT_CSV_TYPE), HttpRequest.GET("/x")), "no Accept header accepts every type");
        assertTrue(meets(accept(MediaType.TEXT_CSV_TYPE), HttpRequest.GET("/x").accept(MediaType.of("text/*"))));
        assertTrue(meets(accept(MediaType.of("text/*")), HttpRequest.GET("/x").accept(MediaType.TEXT_CSV_TYPE)));
        assertTrue(meets(accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_CSV_TYPE), HttpRequest.GET("/x").accept(MediaType.TEXT_CSV_TYPE)));
        assertFalse(meets(accept(MediaType.APPLICATION_JSON_TYPE), HttpRequest.GET("/x").accept(MediaType.TEXT_CSV_TYPE)));

        assertTrue(meets(contentType(MediaType.APPLICATION_JSON_TYPE), HttpRequest.POST("/x", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)));
        assertTrue(meets(contentType(MediaType.of("application/*")), HttpRequest.POST("/x", "{}")
            .header(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8")));
        assertFalse(meets(contentType(MediaType.TEXT_PLAIN_TYPE), HttpRequest.POST("/x", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)));
        assertFalse(meets(contentType(MediaType.ALL_TYPE), HttpRequest.POST("/x", "")), "no content type");

        assertThrows(IllegalArgumentException.class, RequestPredicates::accept);
        assertThrows(IllegalArgumentException.class, RequestPredicates::contentType);
    }

    @Test
    void methodCondition() {
        assertTrue(meets(method(HttpMethod.GET, HttpMethod.HEAD), HttpRequest.HEAD("/x")));
        assertFalse(meets(method(HttpMethod.GET), HttpRequest.POST("/x", "")));
        assertThrows(IllegalArgumentException.class, RequestPredicates::method);
    }

    @Test
    void combinedConditions() {
        MutableHttpRequest<?> request = HttpRequest.GET("/x").header("X-A", "1");

        assertTrue(meets(all(), request));
        assertFalse(meets(any(), request));
        assertTrue(meets(all(header("X-A"), method(HttpMethod.GET)), request));
        assertFalse(meets(all(header("X-A"), header("X-B")), request));
        assertTrue(meets(any(header("X-B"), header("X-A")), request));
        RouteCondition composed = header("X-A").and(header("X-B").negate()).or(queryParam("force"));
        assertTrue(meets(composed, request));
    }

    @Test
    void theConditionsSelectAHandlerRoute() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder routes = new DefaultHttpRouteBuilder(assembly);
        routes.GET("/reports")
            .where(accept(MediaType.TEXT_CSV_TYPE).and(header("X-Export")))
            .handle((request, pathVariables) -> HttpResponse.ok("csv"));
        assembly.addImplicitHeadRoutes();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));

        assertNotNull(router.findClosest(HttpRequest.GET("/reports").header("X-Export", "1").accept(MediaType.TEXT_CSV_TYPE)));
        assertNull(router.findClosest(HttpRequest.GET("/reports").header("X-Export", "1").accept(MediaType.APPLICATION_JSON_TYPE)));
        assertNull(router.findClosest(HttpRequest.GET("/reports").accept(MediaType.TEXT_CSV_TYPE)));
    }

    private static boolean meets(io.micronaut.web.router.builder.RouteCondition condition, io.micronaut.http.HttpRequest<?> request) {
        return io.micronaut.web.router.builder.RouteConditions.matches(condition, request, RouteConditionContext.fallback());
    }
}
