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
package io.micronaut.http.server.netty.util;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With a running Netty server, {@code micronaut.server.host-resolution.allow-forwarded} and {@code allow-xforwarded}
 * allow ignoring the forwarded headers for host resolution: in a controller that uses the resolver and in route host
 * conditions. Requests are written on a socket so that the {@code Host} header differs from the host of the embedded
 * server.
 */
class HostResolutionForwardedHeadersTest {

    private static final String SPEC_NAME = "HostResolutionForwardedHeadersTest";
    private static final String ALLOW_FORWARDED = "micronaut.server.host-resolution.allow-forwarded";
    private static final String ALLOW_XFORWARDED = "micronaut.server.host-resolution.allow-xforwarded";
    private static final String FORWARDED = "Forwarded: host=fwd.example.com;proto=https\r\n";
    private static final String X_FORWARDED = "X-Forwarded-Host: xfwd.example.com\r\nX-Forwarded-Proto: https\r\n";

    @Test
    void byDefaultBothHeaderFamiliesAreRead() throws IOException {
        try (ApplicationContext ctx = start(Map.of())) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            assertEquals("https://fwd.example.com", body(get(server, "/host", "origin.example.com", FORWARDED)));
            assertEquals("https://xfwd.example.com", body(get(server, "/host", "origin.example.com", X_FORWARDED)));
            assertEquals("http://origin.example.com", body(get(server, "/host", "origin.example.com", "")));
        }
    }

    @Test
    void theForwardedHeaderIsIgnoredWhenNotAllowed() throws IOException {
        try (ApplicationContext ctx = start(Map.of(ALLOW_FORWARDED, false))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            assertEquals("http://origin.example.com", body(get(server, "/host", "origin.example.com", FORWARDED)));
            assertEquals("https://xfwd.example.com", body(get(server, "/host", "origin.example.com", FORWARDED + X_FORWARDED)));
        }
    }

    @Test
    void theXForwardedHeadersAreIgnoredWhenNotAllowed() throws IOException {
        try (ApplicationContext ctx = start(Map.of(ALLOW_XFORWARDED, false))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            assertEquals("https://fwd.example.com", body(get(server, "/host", "origin.example.com", FORWARDED)));
            assertEquals("http://origin.example.com", body(get(server, "/host", "origin.example.com", X_FORWARDED)));
        }
    }

    @Test
    void withNoFamilyAllowedARequestWithoutHostResolvesToTheEmbeddedServer() throws IOException {
        try (ApplicationContext ctx = start(bothDisallowed())) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            // an HTTP/1.0 request may omit the Host header, so the embedded server is the remaining source
            String response = send(server, "GET /host HTTP/1.0\r\n" + FORWARDED + X_FORWARDED + "\r\n");
            assertEquals("http://" + server.getHost() + ":" + server.getPort(), body(response));
        }
    }

    @Test
    void aRouteHostConditionReadsTheHostHeaderWhenTheForwardedHeadersAreIgnored() throws IOException {
        String headers = "X-Forwarded-Host: other.example.com\r\nForwarded: host=other.example.com\r\n";
        try (ApplicationContext ctx = start(bothDisallowed())) {
            assertEquals("origin", body(get(ctx.getBean(EmbeddedServer.class), "/site", "origin.example.com", headers)));
        }
    }

    private static Map<String, Object> bothDisallowed() {
        return Map.of(ALLOW_FORWARDED, false, ALLOW_XFORWARDED, false);
    }

    private static ApplicationContext start(Map<String, Object> properties) {
        Map<String, Object> config = new HashMap<>(properties);
        config.put("spec.name", SPEC_NAME);
        config.put("micronaut.server.port", -1);
        ApplicationContext ctx = ApplicationContext.run(config);
        ctx.getBean(EmbeddedServer.class).start();
        return ctx;
    }

    private static String get(EmbeddedServer server, String path, String host, String headers) throws IOException {
        return send(server, "GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\n" + headers + "Connection: close\r\n\r\n");
    }

    private static String send(EmbeddedServer server, String request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    private static void assertStatus(String response, int status) {
        assertTrue(response.matches("(?s)HTTP/1\\.[01] " + status + " .*"), response);
    }

    private static String body(String response) {
        assertStatus(response, 200);
        return response.substring(response.indexOf("\r\n\r\n") + 4);
    }

    @Controller("/host")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class HostController {

        private final HttpHostResolver resolver;

        HostController(HttpHostResolver resolver) {
            this.resolver = resolver;
        }

        @Get
        @Produces(MediaType.TEXT_PLAIN)
        String host(HttpRequest<?> request) {
            return resolver.resolve(request);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Singleton
        HttpRoutes routes() {
            return routes -> {
                routes.GET("/site").where(RequestCondition.host("origin.example.com")).order(-1)
                    .handle((request, pathVariables) -> HttpResponse.ok("origin").contentType(MediaType.TEXT_PLAIN_TYPE));
                routes.GET("/site", (request, pathVariables) -> HttpResponse.ok("other").contentType(MediaType.TEXT_PLAIN_TYPE));
            };
        }
    }
}
