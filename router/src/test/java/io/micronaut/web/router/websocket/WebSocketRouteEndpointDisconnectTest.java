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
package io.micronaut.web.router.websocket;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.context.WebSocketBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The messages of a connection of a message stream route that no handler ever receives are released when the
 * connection closes.
 */
class WebSocketRouteEndpointDisconnectTest {

    @Test
    void theMessagesReadBeforeAnAsynchronousOpenHandlerIsDoneAreReleasedWhenTheConnectionCloses() {
        CompletableFuture<Void> opened = new CompletableFuture<>();
        WebSocketRouteEndpoint endpoint = endpoint(ws -> ws
            .onOpen((session, request) -> opened)
            .onMessageStream(String.class, (session, messages) -> CompletableFuture.completedFuture(null)));
        List<Object> released = new CopyOnWriteArrayList<>();
        WebSocketSession session = session();
        endpoint.connected(session, Runnable::run, error -> { }, released::add);

        endpoint.openMethod().orElseThrow().invoke(session, HttpRequest.GET("/ws"));
        CompletionStage<?> first = (CompletionStage<?>) endpoint.messageMethod().orElseThrow().invoke(session, "first");
        CompletionStage<?> second = (CompletionStage<?>) endpoint.messageMethod().orElseThrow().invoke(session, "second");

        endpoint.disconnected(session);
        opened.complete(null);

        assertEquals(List.of("first", "second"), released);
        assertTrue(first.toCompletableFuture().isDone());
        assertTrue(second.toCompletableFuture().isDone());
    }

    @Test
    void theMessagesReadBeforeAnOpenHandlerFailsAreReleasedWhenTheConnectionCloses() {
        CompletableFuture<Void> opened = new CompletableFuture<>();
        WebSocketRouteEndpoint endpoint = endpoint(ws -> ws
            .onOpen((session, request) -> opened)
            .onMessageStream(String.class, (session, messages) -> CompletableFuture.completedFuture(null)));
        List<Object> released = new CopyOnWriteArrayList<>();
        WebSocketSession session = session();
        endpoint.connected(session, Runnable::run, error -> { }, released::add);

        CompletionStage<?> open = (CompletionStage<?>) endpoint.openMethod().orElseThrow().invoke(session, HttpRequest.GET("/ws"));
        CompletionStage<?> message = (CompletionStage<?>) endpoint.messageMethod().orElseThrow().invoke(session, "message");
        opened.completeExceptionally(new IllegalStateException("cannot open"));
        assertTrue(open.toCompletableFuture().isCompletedExceptionally());

        endpoint.disconnected(session);

        assertEquals(List.of("message"), released);
        assertTrue(message.toCompletableFuture().isDone());
    }

    @Test
    void aSubscriberStillReceivesTheMessagesAndTheCompletionAfterTheConnectionCloses() {
        List<Object> received = new CopyOnWriteArrayList<>();
        WebSocketRouteEndpoint endpoint = endpoint(ws -> ws
            .onMessageStream(String.class, (session, messages) -> {
                CompletableFuture<Void> done = new CompletableFuture<>();
                messages.subscribe(new org.reactivestreams.Subscriber<>() {
                    @Override
                    public void onSubscribe(org.reactivestreams.Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(String s) {
                        received.add(s);
                    }

                    @Override
                    public void onError(Throwable t) {
                        done.completeExceptionally(t);
                    }

                    @Override
                    public void onComplete() {
                        received.add("complete");
                        done.complete(null);
                    }
                });
                return done;
            }));
        List<Object> released = new CopyOnWriteArrayList<>();
        WebSocketSession session = session();
        endpoint.connected(session, Runnable::run, error -> { }, released::add);

        endpoint.openMethod().orElseThrow().invoke(session, HttpRequest.GET("/ws"));
        endpoint.messageMethod().orElseThrow().invoke(session, "message");
        endpoint.disconnected(session);

        assertEquals(List.of("message", "complete"), received);
        assertEquals(List.of(), released);
    }

    private static WebSocketSession session() {
        return (WebSocketSession) Proxy.newProxyInstance(WebSocketSession.class.getClassLoader(), new Class<?>[]{WebSocketSession.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "session";
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static WebSocketRouteEndpoint endpoint(Consumer<WebSocketEndpointSpec> spec) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        HttpRouteBuilder routes = new DefaultHttpRouteBuilder(assembly);
        routes.GET("/ws").webSocket(spec);
        assembly.addImplicitHeadRoutes();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));
        MutableHttpRequest<?> request = HttpRequest.GET("/ws");
        request.header(HttpHeaders.CONNECTION, "Upgrade");
        request.header(HttpHeaders.UPGRADE, "websocket");
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match);
        WebSocketBean<?> bean = match.getRouteInfo().getAttribute(WebSocketRouteEndpoint.ROUTE_ATTRIBUTE, WebSocketBean.class).orElseThrow();
        return (WebSocketRouteEndpoint) bean;
    }
}
