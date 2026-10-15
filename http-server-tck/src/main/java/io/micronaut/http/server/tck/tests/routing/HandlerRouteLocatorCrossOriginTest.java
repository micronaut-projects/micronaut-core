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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.cors.CrossOrigin;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The same located routes reached under two groups with different {@link CrossOrigin} policies answer
 * each with the policy of its own group, whichever group is requested first.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteLocatorCrossOriginTest {
    public static final String SPEC_NAME = "HandlerRouteLocatorCrossOriginTest";
    private static final String PUBLIC_ORIGIN = "https://public.com";
    private static final String PRIVATE_ORIGIN = "https://private.com";

    @Test
    void thePublicGroupFirstDoesNotOpenThePrivateGroup() throws Exception {
        try (ServerUnderTest server = server()) {
            assertAllowed(server, "/public/1/items");
            assertRejected(server, "/private/1/items");
            assertAllowed(server, "/public/1/items");
        }
    }

    @Test
    void thePrivateGroupFirstDoesNotCloseThePublicGroup() throws Exception {
        try (ServerUnderTest server = server()) {
            assertRejected(server, "/private/1/items");
            assertAllowed(server, "/public/1/items");
            assertRejected(server, "/private/1/items");
        }
    }

    @Test
    void eachGroupAllowsItsOwnOrigin() throws Exception {
        try (ServerUnderTest server = server()) {
            assertEquals(List.of(PRIVATE_ORIGIN), allowedOrigins(server, simple("/private/1/items", PRIVATE_ORIGIN)));
            assertEquals(List.of(PUBLIC_ORIGIN), allowedOrigins(server, simple("/public/1/items", PUBLIC_ORIGIN)));
            assertFalse(allowedOrigins(server, simple("/public/1/items", PRIVATE_ORIGIN)).contains(PRIVATE_ORIGIN));
        }
    }

    private static void assertAllowed(ServerUnderTest server, String path) {
        assertEquals(List.of(PUBLIC_ORIGIN), allowedOrigins(server, preflight(path)), path);
        assertEquals(List.of(PUBLIC_ORIGIN), allowedOrigins(server, simple(path, PUBLIC_ORIGIN)), path);
    }

    private static void assertRejected(ServerUnderTest server, String path) {
        assertFalse(allowedOrigins(server, preflight(path)).contains(PUBLIC_ORIGIN), path);
        assertFalse(allowedOrigins(server, simple(path, PUBLIC_ORIGIN)).contains(PUBLIC_ORIGIN), path);
    }

    private static List<String> allowedOrigins(ServerUnderTest server, HttpRequest<?> request) {
        HttpResponse<?> response;
        try {
            response = server.exchange(request, String.class);
        } catch (HttpClientResponseException e) {
            response = e.getResponse();
            assertNotEquals(HttpStatus.OK, response.getStatus());
        }
        return response.getHeaders().getAll(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of());
    }

    private static MutableHttpRequest<?> preflight(String path) {
        return HttpRequest.OPTIONS(path)
            .header(HttpHeaders.ORIGIN, PUBLIC_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name());
    }

    private static MutableHttpRequest<?> simple(String path, String origin) {
        return HttpRequest.GET(path).header(HttpHeaders.ORIGIN, origin);
    }

    private static AnnotationValue<CrossOrigin> crossOrigin(String origin) {
        return AnnotationValue.builder(CrossOrigin.class).member("allowedOrigins", origin).build();
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CrossOriginLocatorRoutes implements HttpRoutes {

        @Override
        public void routes(HttpRouteBuilder routes) {
            LocatedRoutes<?> items = TckLocatedRoutes.of(located -> located.GET("/items", (request, pathVariables) ->
                HttpResponse.ok("item").contentType(MediaType.TEXT_PLAIN_TYPE)));
            routes.path("/public", group -> {
                group.annotate(crossOrigin(PUBLIC_ORIGIN));
                group.locate("/{id}", (request, pathVariables) -> "order", order -> items);
            });
            routes.path("/private", group -> {
                group.annotate(crossOrigin(PRIVATE_ORIGIN));
                group.locate("/{id}", (request, pathVariables) -> "order", order -> items);
            });
        }
    }
}
