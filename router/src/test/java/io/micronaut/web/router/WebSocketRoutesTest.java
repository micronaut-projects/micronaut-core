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
package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Produces;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.route.WebSocketRouteEndpoint;
import io.micronaut.websocket.route.WebSocketRouteSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routes {@link HttpRouteBuilder#webSocket} adds: a {@code GET} route that the server upgrades,
 * which carries the endpoint of handler functions.
 */
class WebSocketRoutesTest {

    @Test
    void aWebSocketRouteIsAGetRouteThatCarriesItsEndpoint() {
        Router router = router(routes -> routes.webSocket("/chat/{room}", ws -> ws
            .subprotocols("chat.v1", "chat.v2")
            .maxPayloadLength(1024)
            .onMessage(String.class, (message, session) -> null)));

        UriRouteInfo<?, ?> route = route(router, HttpRequest.GET("/chat/lobby"));
        assertTrue(route.isWebSocketRoute());
        assertTrue(route.getAnnotationMetadata().hasAnnotation(OnMessage.class));
        assertTrue(route.getAnnotationMetadata().hasAnnotation(OnOpen.class));

        WebSocketRouteEndpoint endpoint = endpoint(route);
        assertEquals("WebSocket route /chat/{room}", endpoint.toString());
        assertSame(endpoint, endpoint.getTarget());
        assertEquals(Optional.of("chat.v1,chat.v2"), endpoint.getSubprotocols());
        MethodExecutionHandle<Object, ?> message = endpoint.messageMethod().orElseThrow();
        assertEquals(OptionalInt.of(1024), message.intValue(OnMessage.class, "maxPayloadLength"));
        assertTrue(message.getReturnType().isAsync());
        // the message type is not bound from the request
        assertSame(message.getArguments()[0], endpoint.messageArgument());
        assertEquals(String.class, endpoint.messageArgument().getType());
        assertTrue(endpoint.openMethod().isEmpty());
        assertTrue(endpoint.closeMethod().isEmpty());
        assertTrue(endpoint.errorMethod().isEmpty());
        assertTrue(endpoint.pongMethod().isEmpty());
        assertNull(endpoint.pongArgument());
        assertThrows(UnsupportedOperationException.class, endpoint::getBeanDefinition);

        // the implicit HEAD route is a WebSocket route too: a plain request to it is an error
        UriRouteInfo<?, ?> head = route(router, HttpRequest.HEAD("/chat/lobby"));
        assertTrue(head.isWebSocketRoute());
        assertSame(endpoint, endpoint(head));
    }

    @Test
    void theHandlersAreCalledWithTheirArguments() {
        AtomicReference<Object> seen = new AtomicReference<>();
        Router router = router(routes -> routes.webSocket("/ws", ws -> ws
            .onOpen((session, request) -> {
                seen.set(request);
                return null;
            })
            .onMessage(Argument.listOf(Integer.class), (message, session) -> CompletableFuture.completedFuture(message))
            .onPong((pong, session) -> {
                seen.set(pong);
                return null;
            })
            .onClose((reason, session) -> {
                seen.set(reason);
                return null;
            })
            .onError((error, session) -> {
                throw new IllegalStateException("from the error handler", error);
            })));
        WebSocketRouteEndpoint endpoint = endpoint(route(router, HttpRequest.GET("/ws")));

        HttpRequest<Object> request = HttpRequest.GET("/ws");
        // a handler without a stage is done
        CompletionStage<?> open = (CompletionStage<?>) endpoint.openMethod().orElseThrow().invoke(null, request);
        assertTrue(open.toCompletableFuture().isDone());
        assertSame(request, seen.get());

        assertEquals(List.of(Integer.class), List.of(endpoint.messageArgument().getTypeParameters()[0].getType()));
        CompletionStage<?> message = (CompletionStage<?>) endpoint.messageMethod().orElseThrow().invoke(List.of(1, 2), null);
        assertEquals(List.of(1, 2), message.toCompletableFuture().join());

        assertEquals(WebSocketPongMessage.class, endpoint.pongArgument().getType());
        CloseReason reason = new CloseReason(4000, "done");
        endpoint.closeMethod().orElseThrow().invoke(reason, null);
        assertSame(reason, seen.get());

        // a handler throws like a method
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> endpoint.errorMethod().orElseThrow().invoke(new RuntimeException("cause"), null));
        assertEquals("from the error handler", error.getMessage());
        assertEquals(Object.class, endpoint.errorMethod().orElseThrow().getReturnType().getTypeParameters()[0].getType());
    }

    @Test
    void theRouteOfAGroupHasThePrefixAndTheAttributes() {
        Router router = router(routes -> routes.path("/rooms", rooms -> {
            rooms.attribute("kind", "room");
            rooms.webSocket("/{id}", ws -> ws.onOpen((session, request) -> null)).attribute("own", 1);
        }));
        UriRouteInfo<?, ?> route = route(router, HttpRequest.GET("/rooms/7"));
        assertEquals("/rooms/{id}", route.getUriMatchTemplate().toString());
        assertEquals("room", route.getAttributes().get("kind"));
        assertEquals(1, route.getAttributes().get("own"));
        assertEquals("WebSocket route /rooms/{id}", endpoint(route).toString());
        assertTrue(route.isWebSocketRoute());
    }

    @Test
    void theAnnotationsGivenToTheRouteKeepItAWebSocketRoute() {
        AnnotationMetadata produces = new DefaultAnnotationMetadata(
            Map.of(Produces.class.getName(), Map.of("value", new String[]{"text/plain"})), Map.of(), Map.of(),
            Map.of(Produces.class.getName(), Map.of("value", new String[]{"text/plain"})), Map.of(), false);
        Router router = router(routes -> routes.webSocket("/annotated", ws -> ws.onOpen((session, request) -> null))
            .annotationMetadata(produces));
        UriRouteInfo<?, ?> route = route(router, HttpRequest.GET("/annotated"));
        assertTrue(route.isWebSocketRoute());
        assertTrue(route.getAnnotationMetadata().hasAnnotation(Produces.class));
    }

    @Test
    void theHandlersAreDeclaredOnceAndInTheLambda() {
        assertThrows(IllegalStateException.class, () -> router(routes -> routes.webSocket("/twice", ws -> ws
            .onMessage(String.class, (message, session) -> null)
            .onMessage(String.class, (message, session) -> null))));
        assertThrows(IllegalArgumentException.class, () -> router(routes -> routes.webSocket("/bad", ws -> ws.subprotocols("a,b"))));
        assertThrows(IllegalArgumentException.class, () -> router(routes -> routes.webSocket("/bad", ws -> ws.maxPayloadLength(0))));

        AtomicReference<WebSocketRouteSpec> escaped = new AtomicReference<>();
        router(routes -> routes.webSocket("/escaped", escaped::set));
        assertThrows(IllegalStateException.class, () -> escaped.get().onOpen((session, request) -> null));
    }

    @Test
    void aPlainRouteIsNotAWebSocketRoute() {
        Router router = router(routes -> routes.GET("/plain", (request, pathVariables) -> io.micronaut.http.HttpResponse.ok()));
        UriRouteInfo<?, ?> route = route(router, HttpRequest.GET("/plain"));
        assertFalse(route.isWebSocketRoute());
        assertTrue(route.getAttribute(WebSocketRouteEndpoint.ROUTE_ATTRIBUTE).isEmpty());
    }

    private static WebSocketRouteEndpoint endpoint(RouteInfo<?> route) {
        WebSocketBean<?> bean = route.getAttribute(WebSocketRouteEndpoint.ROUTE_ATTRIBUTE, WebSocketBean.class).orElseThrow();
        return (WebSocketRouteEndpoint) bean;
    }

    private static UriRouteInfo<?, ?> route(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        return match.getRouteInfo();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
