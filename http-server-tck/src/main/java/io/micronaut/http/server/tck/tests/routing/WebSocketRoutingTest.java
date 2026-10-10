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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The routes of {@link ServerWebSocket} beans: a single route per WebSocket, which only an upgrade
 * request matches, so that a plain HTTP request to its path is answered by another route, or with
 * a {@code 400} if there is none.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class WebSocketRoutingTest {
    public static final String SPEC_NAME = "WebSocketRoutingTest";

    @Test
    void aWebSocketWithOpenAndMessageMethodsHasASingleRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            Router router = server.getApplicationContext().getBean(Router.class);
            List<String> routes = router.uriRoutes()
                .filter(UriRouteInfo::isWebSocketRoute)
                .filter(route -> route.getUriMatchTemplate().toString().startsWith("/ws-"))
                .map(route -> route.getHttpMethodName() + " " + route.getUriMatchTemplate() + " " + route.getTargetMethod().getMethodName())
                .sorted()
                .toList();
            assertEquals(List.of(
                "GET /ws-push/push onOpen",
                "GET /ws-routing/message-only onMessage",
                "GET /ws-routing/open-and-message onOpen",
                "GET /ws-routing/open-only onOpen"
            ), routes);
        }
    }

    @Test
    void aPlainRequestToTheWebSocketPathReachesAnotherRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/ws-push/push"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("fallback push")
                .build());
        }
    }

    @Test
    void aPlainGetToAWebSocketWithoutAnotherRouteIsABadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/ws-routing/open-and-message", "/ws-routing/open-only", "/ws-routing/message-only")) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .body(BodyAssertion.builder().body("Not a WebSocket request").contains())
                    .build());
            }
        }
    }

    @Test
    void anotherMethodToAWebSocketWithoutAnotherRouteIsNotAllowed() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/ws-routing/open-only"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws-routing/open-and-message")
    static class OpenAndMessageWebSocket {
        @OnOpen
        void onOpen() {
        }

        @OnMessage
        void onMessage(String message) {
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws-routing/open-only")
    static class OpenOnlyWebSocket {
        @OnOpen
        void onOpen() {
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws-routing/message-only")
    static class MessageOnlyWebSocket {
        @OnMessage
        void onMessage(String message) {
        }
    }

    /**
     * Like the push of Vaadin, which answers long polling with a plain request to the path of the
     * WebSocket.
     */
    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerWebSocket("/ws-push/push")
    static class PushWebSocket {
        @OnOpen
        void onOpen() {
        }

        @OnMessage
        void onMessage(String message) {
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FallbackRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/ws-push/{+path}")
                .handle((request, pathVariables) -> HttpResponse.ok("fallback " + pathVariables.getString("path")).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
