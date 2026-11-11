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
package io.micronaut.http.server.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A filter method with a {@link MutableHttpRequest} parameter, run after a filter that continued
 * with an {@link HttpRequestWrapper}, which has no mutable view: the parameter is a mutable
 * wrapper of it, whose changes are kept, and the controller still reads the body and sees the
 * connection of the request.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ControllerFilterRequestChangesTest {
    public static final String SPEC_NAME = "ControllerFilterRequestChangesTest";

    @Test
    void aMutableRequestParameterOfAWrappedRequestKeepsTheChangesTheFilterReturned() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("header=added", get(server, HttpRequest.GET("/crc/header")));
        }
    }

    @Test
    void aMutableRequestParameterOfAWrappedRequestKeepsAUriChangedInPlace() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("/crc/uri?marker=changed", get(server, HttpRequest.GET("/crc/uri")));
        }
    }

    @Test
    void theBodyOfAWrappedRequestIsBound() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("body wrapped", get(server, HttpRequest.POST("/crc/body", "wrapped").contentType(MediaType.TEXT_PLAIN_TYPE)));
        }
    }

    @Test
    void theMutableViewOfAWrappedRequestKeepsTheConnectionOfTheRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals(get(server, HttpRequest.GET("/crc-direct/info")), get(server, HttpRequest.GET("/crc/info")));
        }
    }

    private static String get(ServerUnderTest server, HttpRequest<?> request) {
        HttpResponse<String> response = server.exchange(request, String.class);
        assertEquals(200, response.code());
        return response.body();
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    static String describe(HttpRequest<?> request) {
        InetSocketAddress remote = request.getRemoteAddress();
        InetSocketAddress local = request.getServerAddress();
        return "remote=" + remote.getAddress().getHostAddress()
            + " server=" + local.getAddress().getHostAddress() + ":" + local.getPort()
            + " version=" + request.getHttpVersion()
            + " secure=" + request.isSecure();
    }

    @ServerFilter("/crc/**")
    @Order(10)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class WrappingFilter {

        @RequestFilter
        HttpRequest<?> wrap(HttpRequest<?> request) {
            return new HttpRequestWrapper<>(request);
        }
    }

    @ServerFilter("/crc/**")
    @Order(20)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MutatingFilter {

        @RequestFilter
        HttpRequest<?> mutate(MutableHttpRequest<?> request) {
            String path = request.getPath();
            if (path.equals("/crc/header")) {
                return request.header("X-Added", "added");
            }
            if (path.equals("/crc/uri")) {
                // in place, and the request is not returned
                request.uri(URI.create("/crc/uri?marker=changed"));
            }
            return request;
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    static class ChangesController {

        @Get("/crc/header")
        String header(HttpRequest<?> request) {
            return "header=" + request.getHeaders().get("X-Added");
        }

        @Get("/crc/uri")
        String uri(HttpRequest<?> request) {
            return request.getUri().toString();
        }

        @Post("/crc/body")
        @Consumes(MediaType.TEXT_PLAIN)
        String body(@Body String body) {
            return "body " + body;
        }

        @Get("/crc/info")
        String info(HttpRequest<?> request) {
            return describe(request);
        }

        @Get("/crc-direct/info")
        String direct(HttpRequest<?> request) {
            return describe(request);
        }
    }
}
