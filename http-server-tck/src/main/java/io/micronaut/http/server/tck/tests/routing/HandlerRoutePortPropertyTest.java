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
import io.micronaut.context.annotation.Value;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The port of a handler route and of a group given as a property expression,
 * {@link io.micronaut.web.router.builder.HttpRouteSpec#port(String)} and
 * {@link io.micronaut.web.router.builder.HttpRouteGroup#port(String)}: resolved like the port of
 * a {@code @Controller(port = "${...}")}, including a default, and served on that port only.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRoutePortPropertyTest {
    public static final String SPEC_NAME = "HandlerRoutePortPropertyTest";
    private static final String PORT_PROPERTY = "handler-route-port-property-test.port";

    @Test
    void theRoutesOnAPortPropertyAnswerOnThatPortOnlyLikeAControllerWithThePortProperty() throws Exception {
        int port = SocketUtils.findAvailableTcpPort();
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider()
            .getServer(SPEC_NAME, Map.of(PORT_PROPERTY, port))) {
            for (String path : new String[]{"/port-property/group", "/port-property/route", "/port-property/default", "/port-property-controller"}) {
                assertEquals(200, status(port, path), path);
                assertEquals("ported", body(port, path), path);
                AssertionUtils.assertThrows(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .build());
            }
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/port-property/plain"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("plain")
                .build());
        }
    }

    private static int status(int port, String path) throws IOException, InterruptedException {
        return send(port, path).statusCode();
    }

    private static String body(int port, String path) throws IOException, InterruptedException {
        return send(port, path).body();
    }

    private static java.net.http.HttpResponse<String> send(int port, String path) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        }
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PortPropertyRoutes implements HttpRoutes {
        private final int port;

        PortPropertyRoutes(@Value("${" + PORT_PROPERTY + "}") int port) {
            this.port = port;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/port-property/group", group -> {
                group.port("${" + PORT_PROPERTY + "}");
                group.GET((request, pathVariables) -> text("ported"));
            });
            routes.GET("/port-property/route", (request, pathVariables) -> text("ported")).port("${" + PORT_PROPERTY + "}");
            // the default of a property that is not set
            routes.GET("/port-property/default", (request, pathVariables) -> text("ported"))
                .port("${handler-route-port-property-test.unset:" + port + "}");
            routes.GET("/port-property/plain", (request, pathVariables) -> text("plain"));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller(value = "/port-property-controller", port = "${" + PORT_PROPERTY + "}")
    static class PortPropertyController {
        @Get
        @Produces(MediaType.TEXT_PLAIN)
        String ported() {
            return "ported";
        }
    }
}
