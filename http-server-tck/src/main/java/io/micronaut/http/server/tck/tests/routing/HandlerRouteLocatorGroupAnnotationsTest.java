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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A located route has the annotations of the groups of its locator route, like a route declared
 * in those groups: a filter bound to a {@link FilterMatcher} annotation of the group runs for it,
 * and the {@link Produces} of the group selects it and gives its response a content type, unless
 * the located route has annotations of its own.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteLocatorGroupAnnotationsTest {
    public static final String SPEC_NAME = "HandlerRouteLocatorGroupAnnotationsTest";

    @Test
    void aFilterBoundToAnAnnotationOfTheGroupRunsForTheLocatedRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/audited/direct", "/audited/1/item")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .header("X-Audited", "true")
                    .build());
            }
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/plain/1/item"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> assertFalse(response.getHeaders().contains("X-Audited")))
                .build());
        }
    }

    @Test
    void theProducesOfTheGroupGivesTheLocatedResponseItsContentType() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/text/direct", "/text/1/item")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("item")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                    .build());
            }
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/text/1/html"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                .build());
        }
    }

    @Test
    void theProducesOfTheGroupSelectsTheLocatedRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/text/direct", "/text/1/item")) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(path).accept(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.NOT_ACCEPTABLE)
                    .build());
            }
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/text/1/item").accept(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("item")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of());
    }

    private static AnnotationValue<Produces> produces(String mediaType) {
        return AnnotationValue.builder(Produces.class).member("value", new String[]{mediaType}).build();
    }

    /**
     * Binds {@link AuditFilter} to the routes that carry it.
     */
    @FilterMatcher
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface Audited {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class GroupAnnotationsLocatorRoutes implements HttpRoutes {

        @Override
        public void routes(HttpRouteBuilder routes) {
            LocatedRoutes<?> items = TckLocatedRoutes.of(located -> {
                located.GET("/item", (request, pathVariables) -> HttpResponse.ok("item"));
                located.GET("/html").annotate(produces(MediaType.TEXT_HTML))
                    .handle((request, pathVariables) -> HttpResponse.ok("<p>item</p>"));
            });
            routes.path("/audited", group -> {
                group.annotate(Audited.class);
                group.GET("/direct", (request, pathVariables) -> HttpResponse.ok("item"));
                group.locate("/{id}", (request, pathVariables) -> "order", order -> items);
            });
            routes.path("/plain", group -> group.locate("/{id}", (request, pathVariables) -> "order", order -> items));
            routes.path("/text", group -> {
                group.annotate(produces(MediaType.TEXT_PLAIN));
                group.GET("/direct", (request, pathVariables) -> HttpResponse.ok("item"));
                group.locate("/{id}", (request, pathVariables) -> "order", order -> items);
            });
        }
    }

    @Audited
    @ServerFilter("/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AuditFilter {
        @ResponseFilter
        void audit(MutableHttpResponse<?> response) {
            response.header("X-Audited", "true");
        }
    }
}
