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

        assertTrue(header("x-mode").test(request), "the name is case-insensitive");
        assertFalse(header("X-Other").test(request));
        assertTrue(header("X-Mode", "b").test(request), "any of the values");
        assertFalse(header("X-Mode", "B").test(request), "the value is case-sensitive");
        assertTrue(header("X-Mode", value -> value.startsWith("a")).test(request));
        assertFalse(header("X-Other", value -> true).test(request));
    }

    @Test
    void queryParameterConditions() {
        MutableHttpRequest<?> request = HttpRequest.GET("/x");
        request.getParameters().add("format", List.of("csv", "json")).add("debug", List.of(""));

        assertTrue(queryParam("debug").test(request));
        assertFalse(queryParam("Format").test(request), "the name is case-sensitive");
        assertTrue(queryParam("format", "json").test(request), "any of the values");
        assertFalse(queryParam("format", "xml").test(request));
        assertTrue(queryParam("format", value -> value.length() == 3).test(request));
    }

    @Test
    void mediaTypeConditions() {
        assertTrue(accept(MediaType.TEXT_CSV_TYPE).test(HttpRequest.GET("/x")), "no Accept header accepts every type");
        assertTrue(accept(MediaType.TEXT_CSV_TYPE).test(HttpRequest.GET("/x").accept(MediaType.of("text/*"))));
        assertTrue(accept(MediaType.of("text/*")).test(HttpRequest.GET("/x").accept(MediaType.TEXT_CSV_TYPE)));
        assertTrue(accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_CSV_TYPE).test(HttpRequest.GET("/x").accept(MediaType.TEXT_CSV_TYPE)));
        assertFalse(accept(MediaType.APPLICATION_JSON_TYPE).test(HttpRequest.GET("/x").accept(MediaType.TEXT_CSV_TYPE)));

        assertTrue(contentType(MediaType.APPLICATION_JSON_TYPE).test(HttpRequest.POST("/x", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)));
        assertTrue(contentType(MediaType.of("application/*")).test(HttpRequest.POST("/x", "{}")
            .header(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8")));
        assertFalse(contentType(MediaType.TEXT_PLAIN_TYPE).test(HttpRequest.POST("/x", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)));
        assertFalse(contentType(MediaType.ALL_TYPE).test(HttpRequest.POST("/x", "")), "no content type");

        assertThrows(IllegalArgumentException.class, RequestPredicates::accept);
        assertThrows(IllegalArgumentException.class, RequestPredicates::contentType);
    }

    @Test
    void methodCondition() {
        assertTrue(method(HttpMethod.GET, HttpMethod.HEAD).test(HttpRequest.HEAD("/x")));
        assertFalse(method(HttpMethod.GET).test(HttpRequest.POST("/x", "")));
        assertThrows(IllegalArgumentException.class, RequestPredicates::method);
    }

    @Test
    void combinedConditions() {
        MutableHttpRequest<?> request = HttpRequest.GET("/x").header("X-A", "1");

        assertTrue(all().test(request));
        assertFalse(any().test(request));
        assertTrue(all(header("X-A"), method(HttpMethod.GET)).test(request));
        assertFalse(all(header("X-A"), header("X-B")).test(request));
        assertTrue(any(header("X-B"), header("X-A")).test(request));
        Predicate<HttpRequest<?>> composed = header("X-A").and(header("X-B").negate()).or(queryParam("force"));
        assertTrue(composed.test(request));
    }

    @Test
    void theConditionsSelectAHandlerRoute() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder routes = new DefaultHttpRouteBuilder(assembly);
        routes.GET("/reports", (request, pathVariables) -> HttpResponse.ok("csv"))
            .where(accept(MediaType.TEXT_CSV_TYPE).and(header("X-Export")));
        assembly.addImplicitHeadRoutes();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));

        assertNotNull(router.findClosest(HttpRequest.GET("/reports").header("X-Export", "1").accept(MediaType.TEXT_CSV_TYPE)));
        assertNull(router.findClosest(HttpRequest.GET("/reports").header("X-Export", "1").accept(MediaType.APPLICATION_JSON_TYPE)));
        assertNull(router.findClosest(HttpRequest.GET("/reports").accept(MediaType.TEXT_CSV_TYPE)));
    }
}
