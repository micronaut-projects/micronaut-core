/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.tck.tests.cors;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.cors.CrossOrigin;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.CorsUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.server.tck.CorsUtils.*;
import static io.micronaut.http.server.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public class CrossOriginTest {

    private static final String SPECNAME = "CrossOriginTest";

    @Test
    void crossOriginAnnotationWithMatchingOrigin() throws IOException {
        asserts(SPECNAME,
            preflight(UriBuilder.of("/foo").path("bar"), "https://foo.com", HttpMethod.GET),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertCorsHeaders(response, "https://foo.com", HttpMethod.GET);
                    assertFalse(response.getHeaders().names().contains(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
                })
                .build()));
    }

    @Test
    void crossOriginAnnotationWithNoMatchingOrigin() throws IOException {
        asserts(SPECNAME,
            preflight(UriBuilder.of("/foo").path("bar"), "https://bar.com", HttpMethod.GET),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .assertResponse(CorsUtils::assertCorsHeadersNotPresent)
                .build()));
    }

    @Test
    void verifyHttpMethodIsValidatedInACorsRequest() {
        assertAll(
            () -> asserts(SPECNAME,
                preflight(UriBuilder.of("/methods").path("getit"), "https://www.google.com", HttpMethod.GET),
                (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        assertCorsHeaders(response, "https://www.google.com", HttpMethod.GET);
                        assertFalse(response.getHeaders().names().contains(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
                    })
                    .build())),
            () -> asserts(SPECNAME,
                preflight(UriBuilder.of("/methods").path("postit").path("id"), "https://www.google.com", HttpMethod.POST),
                (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        assertCorsHeaders(response, "https://www.google.com", HttpMethod.POST);
                        assertFalse(response.getHeaders().names().contains(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
                    })
                    .build())),
            () -> asserts(SPECNAME,
                preflight(UriBuilder.of("/methods").path("deleteit").path("id"), "https://www.google.com", HttpMethod.DELETE),
                (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .assertResponse(CorsUtils::assertCorsHeadersNotPresent)
                    .build()))
        );
    }

    @Test
    void allowedHeadersHappyPath() throws IOException {
        asserts(SPECNAME,
            preflight(UriBuilder.of("/allowedheaders").path("bar"), "https://foo.com", HttpMethod.GET)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION + "," + HttpHeaders.CONTENT_TYPE),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertCorsHeaders(response, "https://foo.com", HttpMethod.GET);
                    assertTrue(response.getHeaders().names().contains(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
                })
                .build()));
    }

    @Test
    void allowedHeadersFailure() throws IOException {
        asserts(SPECNAME,
            preflight(UriBuilder.of("/allowedheaders").path("bar"), "https://foo.com", HttpMethod.GET)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "foo"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .assertResponse(CorsUtils::assertCorsHeadersNotPresent)
                .build()));
    }

    @Test
    void exposedHeadersHappyPath() throws IOException {
        asserts(SPECNAME,
            preflight(UriBuilder.of("/exposedheaders").path("bar"), "https://foo.com", HttpMethod.GET)
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.AUTHORIZATION + "," + HttpHeaders.CONTENT_TYPE),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertCorsHeaders(response, "https://foo.com", HttpMethod.GET);
                    assertTrue(response.getHeaders().names().contains(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
                })
                .build()));
    }

    @Test
    void exposedHeadersFailure() throws IOException {
        asserts(SPECNAME,
            preflight(UriBuilder.of("/exposedheaders").path("bar"), "https://foo.com", HttpMethod.GET)
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "foo"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .assertResponse(this::assertCorsHeadersNotPresent)
                .build()));
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/foo")
    static class Foo {
        @CrossOrigin("https://foo.com")
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/bar")
        String index() {
            return "bar";
        }
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/methods")
    @CrossOrigin(
        allowedOrigins = "https://www.google.com",
        allowedMethods = { HttpMethod.GET, HttpMethod.POST }
    )
    static class AllowedMethods {
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/getit")
        String canGet() {
            return "get";
        }

        @Produces(MediaType.TEXT_PLAIN)
        @Post("/postit/{id}")
        String canPost(@PathVariable String id) {
            return id;
        }

        @Delete("/deleteit/{id}")
        String cantDelete(@PathVariable String id) {
            return id;
        }
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/allowedheaders")
    @CrossOrigin(
        value = "https://foo.com",
        allowedHeaders = { HttpHeaders.CONTENT_TYPE, HttpHeaders.AUTHORIZATION }
    )
    static class AllowedHeaders {
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/bar")
        String index() {
            return "bar";
        }
    }
    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/exposedheaders")
    @CrossOrigin(
        value = "https://foo.com",
        exposedHeaders = { HttpHeaders.CONTENT_TYPE, HttpHeaders.AUTHORIZATION }
    )
    static class ExposedHeaders {
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/bar")
        String index() {
            return "bar";
        }
    }

    // TODO: tests for CrossOrigin.allowCredentials, CrossOrigin.maxAge
    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/credentials")
    @CrossOrigin(
        value = "https://foo.com",
        allowCredentials = "false"
    )
    static class Credentials {
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/bar")
        String index() {
            return "bar";
        }
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Replaces(HttpHostResolver.class)
    @Singleton
    static class HttpHostResolverReplacement implements HttpHostResolver {
        @Override
        public String resolve(@Nullable HttpRequest request) {
            return "https://micronautexample.com";
        }
    }

    private static MutableHttpRequest<?> preflight(UriBuilder uriBuilder, String originValue, HttpMethod method) {
        return HttpRequest.OPTIONS(uriBuilder.build())
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
            .header(HttpHeaders.ORIGIN, originValue)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
    }

}
