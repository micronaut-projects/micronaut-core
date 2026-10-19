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
package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The handlers of a WebSocket run in the propagated context of the upgrade request, with the
 * elements that a filter added to it.
 */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class WebSocketFilterContextTest {

    private static final String SPEC = "WebSocketFilterContextTest";

    private static ApplicationContext context;
    private static HttpClient http;
    private static int port;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of("spec.name", SPEC, "micronaut.server.port", -1));
        port = context.getBean(EmbeddedServer.class).start().getPort();
        http = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        http.shutdownNow();
        context.close();
    }

    @Test
    void theHandlersOfAWebSocketRouteSeeTheContextOfTheFilter() throws Exception {
        assertSeesTrace("/s/route");
    }

    @Test
    void theMethodsOfAServerWebSocketSeeTheContextOfTheFilter() throws Exception {
        assertSeesTrace("/s/bean");
    }

    private static void assertSeesTrace(String path) throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        WebSocket ws = http.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + port + path), new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                received.add(data.toString());
                webSocket.request(1);
                return null;
            }
        }).get(10, TimeUnit.SECONDS);
        assertEquals("open " + path, received.poll(10, TimeUnit.SECONDS));
        ws.sendText("hi", true).get(10, TimeUnit.SECONDS);
        assertEquals("message " + path, received.poll(10, TimeUnit.SECONDS));
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(10, TimeUnit.SECONDS);
    }

    private static String trace() {
        return PropagatedContext.getOrEmpty().find(Trace.class).map(Trace::path).orElse("none");
    }

    record Trace(String path) implements PropagatedContextElement {
    }

    @ServerFilter("/s/**")
    @Requires(property = "spec.name", value = SPEC)
    static class TraceFilter {
        @RequestFilter
        void filter(HttpRequest<?> request, MutablePropagatedContext context) {
            context.add(new Trace(request.getPath()));
        }
    }

    @ServerWebSocket("/s/bean")
    @Requires(property = "spec.name", value = SPEC)
    static class TraceSocket {
        @OnOpen
        CompletionStage<?> onOpen(WebSocketSession session) {
            return session.sendAsync("open " + trace());
        }

        @OnMessage
        CompletionStage<?> onMessage(String message, WebSocketSession session) {
            return session.sendAsync("message " + trace());
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC)
    static class Routes {
        @Singleton
        HttpRoutes traceRoutes() {
            return routes -> routes.GET("/s/route").webSocket(ws -> ws
                .onOpen((session, request) -> session.sendAsync("open " + trace()))
                .onMessage(String.class, (session, message) -> session.sendAsync("message " + trace())));
        }
    }
}
