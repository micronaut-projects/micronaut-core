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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.annotation.PreMatching;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code void} filter method that changes the URI of its {@link MutableHttpRequest} parameter in
 * place changes the request the chain continues with: a pre-matching filter changes the route that
 * is matched, the body and the connection of the request are kept, and a filter after the route
 * match changes the URI the controller sees.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ControllerFilterInPlaceUriChangeTest {
    public static final String SPEC_NAME = "ControllerFilterInPlaceUriChangeTest";

    @Test
    void aPreMatchingFilterThatChangesTheUriInPlaceChangesTheMatchedRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("new item name=query", get(server, HttpRequest.GET("/cip/old/item")));
        }
    }

    @Test
    void aPreMatchingFilterThatChangesTheUriInPlaceKeepsTheBody() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("echo moved", get(server, HttpRequest.POST("/cip/old/echo", "moved").contentType(MediaType.TEXT_PLAIN_TYPE)));
        }
    }

    @Test
    void aPreMatchingFilterThatChangesTheUriInPlaceKeepsTheConnectionOfTheRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals(get(server, HttpRequest.GET("/cip/info")), get(server, HttpRequest.GET("/cip/old-info")));
        }
    }

    @Test
    void aFilterThatChangesTheUriInPlaceAfterTheRouteMatchChangesTheUriTheControllerSees() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("/cip/mark?marker=changed", get(server, HttpRequest.GET("/cip/mark")));
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

    @ServerFilter("/cip/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class InPlaceFilters {

        @RequestFilter
        @PreMatching
        void moved(MutableHttpRequest<?> request) {
            String path = request.getPath();
            if (path.equals("/cip/old-info")) {
                request.uri(URI.create("/cip/info"));
            } else if (path.equals("/cip/old/echo")) {
                request.uri(URI.create("/cip/new-echo"));
            } else if (path.startsWith("/cip/old/")) {
                request.uri(URI.create("/cip/new/" + path.substring("/cip/old/".length()) + "?name=query"));
            }
        }

        @RequestFilter
        void marked(MutableHttpRequest<?> request) {
            if (request.getPath().equals("/cip/mark")) {
                request.uri(URI.create("/cip/mark?marker=changed"));
            }
        }
    }

    @Controller("/cip")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    static class InPlaceController {

        @Get("/new/{name}")
        String changed(@PathVariable String name, HttpRequest<?> request) {
            return "new " + name + " name=" + request.getParameters().get("name");
        }

        @Post("/new-echo")
        @Consumes(MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            return "echo " + body;
        }

        @Get("/mark")
        String mark(HttpRequest<?> request) {
            return request.getUri().toString();
        }

        @Get("/info")
        String info(HttpRequest<?> request) {
            InetSocketAddress remote = request.getRemoteAddress();
            InetSocketAddress local = request.getServerAddress();
            return "remote=" + remote.getAddress().getHostAddress()
                + " server=" + local.getAddress().getHostAddress() + ":" + local.getPort()
                + " version=" + request.getHttpVersion()
                + " secure=" + request.isSecure();
        }
    }
}
