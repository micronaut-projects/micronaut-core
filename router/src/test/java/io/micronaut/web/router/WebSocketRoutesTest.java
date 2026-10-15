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
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.websocket.WebSocketEndpointSpec;
import io.micronaut.web.router.websocket.WebSocketMessageHandler;
import io.micronaut.web.router.websocket.WebSocketRouteEndpoint;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketPingMessage;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.context.WebSocketBean;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
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
 * The routes {@link io.micronaut.web.router.builder.HttpRouteSpec#webSocket} adds: a {@code GET} route that the server upgrades,
 * which carries the endpoint of handler functions.
 */
class WebSocketRoutesTest {

    @Test
    void aWebSocketRouteIsAGetRouteThatCarriesItsEndpoint() {
        Router router = router(routes -> routes.GET("/chat/{room}").webSocket(ws -> ws
            .subprotocols("chat.v1", "chat.v2")
            .maxPayloadLength(1024)
            .onMessage(String.class, (session, message) -> null)));

        UriRouteInfo<?, ?> route = route(router, HttpRequest.GET("/chat/lobby"));
        assertTrue(route.isWebSocketRoute());
        assertTrue(route.getAnnotationMetadata().hasAnnotation(OnMessage.class));
        assertTrue(route.getAnnotationMetadata().hasAnnotation(OnOpen.class));

        WebSocketRouteEndpoint endpoint = endpoint(route);
        assertEquals("WebSocket route /chat/{room}", endpoint.toString());
        assertSame(endpoint, endpoint.getTarget());
        assertEquals(List.of("chat.v1", "chat.v2"), endpoint.getSubprotocols());
        MethodExecutionHandle<Object, ?> message = endpoint.messageMethod().orElseThrow();
        assertEquals(OptionalInt.of(1024), message.intValue(OnMessage.class, "maxPayloadLength"));
        assertTrue(message.getReturnType().isAsync());
        // the message type is not bound from the request
        assertSame(message.getArguments()[1], endpoint.messageArgument());
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
        Router router = router(routes -> routes.GET("/ws").webSocket(ws -> ws
            .onOpen((session, request) -> {
                seen.set(request);
                return null;
            })
            .onMessage(Argument.listOf(Integer.class), (session, message) -> CompletableFuture.completedFuture(message))
            .onPong((session, pong) -> {
                seen.set(pong);
                return null;
            })
            .onClose((session, reason) -> {
                seen.set(reason);
                return null;
            })
            .onError((session, error) -> {
                throw new IllegalStateException("from the error handler", error);
            })));
        WebSocketRouteEndpoint endpoint = endpoint(route(router, HttpRequest.GET("/ws")));

        HttpRequest<Object> request = HttpRequest.GET("/ws");
        // a handler without a stage is done
        CompletionStage<?> open = (CompletionStage<?>) endpoint.openMethod().orElseThrow().invoke(null, request);
        assertTrue(open.toCompletableFuture().isDone());
        assertSame(request, seen.get());

        assertEquals(List.of(Integer.class), List.of(endpoint.messageArgument().getTypeParameters()[0].getType()));
        CompletionStage<?> message = (CompletionStage<?>) endpoint.messageMethod().orElseThrow().invoke(null, List.of(1, 2));
        assertEquals(List.of(1, 2), message.toCompletableFuture().join());

        assertEquals(WebSocketPongMessage.class, endpoint.pongArgument().getType());
        CloseReason reason = new CloseReason(4000, "done");
        endpoint.closeMethod().orElseThrow().invoke(null, reason);
        assertSame(reason, seen.get());

        // a handler throws like a method
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> endpoint.errorMethod().orElseThrow().invoke(null, new RuntimeException("cause")));
        assertEquals("from the error handler", error.getMessage());
        assertEquals(Object.class, endpoint.errorMethod().orElseThrow().getReturnType().getTypeParameters()[0].getType());
    }

    @Test
    void theRouteOfAGroupHasThePrefixAndTheAttributes() {
        Router router = router(routes -> routes.path("/rooms", rooms -> {
            rooms.attribute("kind", "room");
            rooms.GET("/{id}").attribute("own", 1).webSocket(ws -> ws.onOpen((session, request) -> null));
        }));
        UriRouteInfo<?, ?> route = route(router, HttpRequest.GET("/rooms/7"));
        assertEquals("/rooms/{id}", route.getUriMatchTemplate().toString());
        assertEquals("room", route.getAttributes().get("kind"));
        assertEquals(1, route.getAttributes().get("own"));
        assertEquals("WebSocket route /rooms/{id}", endpoint(route).toString());
        assertTrue(route.isWebSocketRoute());
    }

    @Test
    void theRouteOfAGroupHasTheExecutorOfTheGroupButNotItsMediaTypes() {
        Router plain = router(routes -> routes.GET("/rooms/{id}").webSocket(ws -> ws.onOpen((session, request) -> null)));
        Router grouped = router(routes -> routes.path("/rooms", rooms -> {
            rooms.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE).executeOn("group-executor");
            rooms.GET("/{id}").webSocket(ws -> ws.onOpen((session, request) -> null));
        }));
        UriRouteInfo<?, ?> route = route(grouped, HttpRequest.GET("/rooms/7").accept(MediaType.APPLICATION_JSON_TYPE));
        UriRouteInfo<?, ?> expected = route(plain, HttpRequest.GET("/rooms/7"));
        assertTrue(route.isWebSocketRoute());
        assertEquals(expected.getConsumes(), route.getConsumes());
        assertEquals(expected.getProduces(), route.getProduces());
        // no executor of that name in this router
        Exception error = assertThrows(Exception.class, () -> route.getExecutor(ThreadSelection.MANUAL));
        assertTrue(error.getMessage().contains("group-executor"), error.getMessage());
    }

    @Test
    void theAnnotationsGivenToTheRouteKeepItAWebSocketRoute() {
        AnnotationMetadata produces = new DefaultAnnotationMetadata(
            Map.of(Produces.class.getName(), Map.of("value", new String[]{"text/plain"})), Map.of(), Map.of(),
            Map.of(Produces.class.getName(), Map.of("value", new String[]{"text/plain"})), Map.of(), false);
        AnnotationMetadataProvider annotated = new AnnotationMetadataProvider() {
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return produces;
            }
        };
        Router router = router(routes -> routes.GET("/annotated")
            .annotationMetadata(annotated)
            .webSocket(ws -> ws.onOpen((session, request) -> null)));
        UriRouteInfo<?, ?> route = route(router, HttpRequest.GET("/annotated"));
        assertTrue(route.isWebSocketRoute());
        assertTrue(route.getAnnotationMetadata().hasAnnotation(Produces.class));
    }

    @Test
    void aWebSocketRouteWithoutAPathIsAtThePrefixOfItsGroupAndKeepsItsAnnotationsWhenAnnotated() {
        Router router = router(routes -> routes.path("/chat", chat -> {
            chat.annotate(Produces.class, produces -> produces.value("text/plain"));
            chat.GET("/").webSocket(ws -> ws.onOpen((session, request) -> null));
        }));
        UriRouteInfo<?, ?> route = route(router, HttpRequest.GET("/chat"));
        assertEquals("/chat", route.getUriMatchTemplate().toString());
        assertTrue(route.isWebSocketRoute());
        assertTrue(route.getAnnotationMetadata().hasAnnotation(Produces.class));
    }

    @Test
    void theHandlersAreDeclaredOnceAndInTheLambda() {
        assertThrows(IllegalStateException.class, () -> router(routes -> routes.GET("/twice").webSocket(ws -> ws
            .onMessage(String.class, (session, message) -> null)
            .onMessage(String.class, (session, message) -> null))));
        assertThrows(IllegalArgumentException.class, () -> router(routes -> routes.GET("/bad").webSocket(ws -> ws.subprotocols("a,b"))));
        assertThrows(IllegalArgumentException.class, () -> router(routes -> routes.GET("/bad").webSocket(ws -> ws.maxPayloadLength(0))));

        AtomicReference<WebSocketEndpointSpec> escaped = new AtomicReference<>();
        router(routes -> routes.GET("/escaped").webSocket(escaped::set));
        assertThrows(IllegalStateException.class, () -> escaped.get().onOpen((session, request) -> null));
    }

    @Test
    void aRouteHasAMessageHandlerOrAMessageStreamHandler() {
        assertThrows(IllegalStateException.class, () -> router(routes -> routes.GET("/both").webSocket(ws -> ws
            .onMessage(String.class, (session, message) -> null)
            .onMessageStream(String.class, (session, messages) -> null))));
        assertThrows(IllegalStateException.class, () -> router(routes -> routes.GET("/both").webSocket(ws -> ws
            .onMessageStream(String.class, (session, messages) -> null)
            .onMessage(String.class, (session, message) -> null))));
        assertThrows(IllegalArgumentException.class, () -> router(routes -> routes.GET("/bad").webSocket(ws -> ws.maxConcurrentMessages(0))));
        // a message stream receives its messages one after the other
        IllegalStateException concurrent = assertThrows(IllegalStateException.class, () -> router(routes -> routes.GET("/stream").webSocket(ws -> ws
            .maxConcurrentMessages(4)
            .onMessageStream(String.class, (session, messages) -> null))));
        assertTrue(concurrent.getMessage().contains("maxConcurrentMessages"), concurrent.getMessage());

        Router router = router(routes -> routes.GET("/stream").webSocket(ws -> ws
            .maxPayloadLength(1024)
            .onMessageStream(Argument.listOf(Integer.class), (session, messages) -> null)
            .onPing((session, ping) -> null)));
        WebSocketRouteEndpoint endpoint = endpoint(route(router, HttpRequest.GET("/stream")));
        assertEquals(1, endpoint.maxConcurrentMessages());
        // the messages are decoded like for a message handler, and the stream starts on the opening
        MethodExecutionHandle<Object, ?> message = endpoint.messageMethod().orElseThrow();
        assertEquals(OptionalInt.of(1024), message.intValue(OnMessage.class, "maxPayloadLength"));
        assertSame(message.getArguments()[1], endpoint.messageArgument());
        assertEquals(Integer.class, endpoint.messageArgument().getTypeParameters()[0].getType());
        assertTrue(endpoint.openMethod().isPresent());
        assertEquals(WebSocketPingMessage.class, endpoint.pingArgument().getType());
        assertNotNull(endpoint.pingMethod());

        // one message at a time by default
        Router plain = router(routes -> routes.GET("/plain").webSocket(ws -> ws.onOpen((session, request) -> null)));
        WebSocketRouteEndpoint plainEndpoint = endpoint(route(plain, HttpRequest.GET("/plain")));
        assertEquals(1, plainEndpoint.maxConcurrentMessages());
        assertNull(plainEndpoint.pingMethod());
        assertNull(plainEndpoint.pingArgument());
    }

    @Test
    void aWebSocketRouteHasNoMediaTypesAndNoResponseType() {
        for (Consumer<HttpRouteSpec> setting : List.<Consumer<HttpRouteSpec>>of(
            route -> route.produces(MediaType.TEXT_PLAIN_TYPE),
            route -> route.consumes(MediaType.APPLICATION_XML_TYPE),
            route -> route.consumesAll(),
            route -> route.responseType(String.class))) {
            AtomicReference<IllegalStateException> error = new AtomicReference<>();
            Router router = router(routes -> {
                HttpRouteSpec route = routes.GET("/typed");
                setting.accept(route);
                error.set(assertThrows(IllegalStateException.class, () -> route.webSocket(ws -> ws.onOpen((session, request) -> null))));
            });
            assertTrue(error.get().getMessage().contains("no media types or response type"), error.get().getMessage());
            // the route is dropped
            assertNull(router.findClosest(HttpRequest.GET("/typed")));
        }
    }

    @Test
    void aLocatedRouteIsNotAWebSocketRoute() {
        AtomicReference<IllegalStateException> error = new AtomicReference<>();
        LocatedRoutes<?> located = TestLocatedRoutes.of(target -> {
            target.GET("/plain").respond(io.micronaut.http.HttpResponse.ok());
            error.set(assertThrows(IllegalStateException.class, () -> target.GET("/ws").webSocket(ws -> ws.onOpen((session, request) -> null))));
        });
        Router router = router(routes -> routes.locate("/targets/{id}", (request, pathVariables) -> "target", target -> located));
        // the located routes are declared, the WebSocket route is dropped
        assertNotNull(router.findClosest(HttpRequest.GET("/targets/1/plain")));
        assertNull(router.findClosest(HttpRequest.GET("/targets/1/ws")));
        assertTrue(error.get().getMessage().contains("is a located route"), error.get().getMessage());
    }

    @Test
    void aHandlerOfASupertypeHandlesTheMessages() {
        WebSocketMessageHandler<Object> any = (session, message) -> null;
        Router router = router(routes -> routes.GET("/any").webSocket(ws -> ws
            .onMessage(String.class, any)
            .onPing(any)
            .onPong(any)));
        WebSocketRouteEndpoint endpoint = endpoint(route(router, HttpRequest.GET("/any")));
        assertEquals(String.class, endpoint.messageArgument().getType());
    }

    @Test
    void theMostPendingMessagesAreBoundedByDefault() {
        Router router = router(routes -> {
            routes.GET("/default").webSocket(ws -> ws.onMessage(String.class, (session, message) -> null));
            routes.GET("/unbounded").webSocket(ws -> ws.maxPendingMessages(0).onMessage(String.class, (session, message) -> null));
        });
        assertEquals(16, endpoint(route(router, HttpRequest.GET("/default"))).maxPendingMessages());
        assertEquals(0, endpoint(route(router, HttpRequest.GET("/unbounded"))).maxPendingMessages());
        assertThrows(IllegalArgumentException.class, () -> router(routes -> routes.GET("/bad").webSocket(ws -> ws.maxPendingMessages(-1))));
    }

    @Test
    void onlyARouteOfGetIsAWebSocketRoute() {
        assertThrows(IllegalStateException.class, () -> router(routes -> routes.POST("/post").webSocket(ws -> ws.onOpen((session, request) -> null))));
        assertThrows(IllegalStateException.class, () -> router(routes -> routes.any("/any").webSocket(ws -> ws.onOpen((session, request) -> null))));
        assertThrows(IllegalStateException.class, () -> router(routes -> routes.route(Set.of(HttpMethod.GET, HttpMethod.POST), "/both")
            .webSocket(ws -> ws.onOpen((session, request) -> null))));
        // a route of GET only, however it is declared
        Router router = router(routes -> routes.route(Set.of(HttpMethod.GET), "/set").webSocket(ws -> ws.onOpen((session, request) -> null)));
        assertTrue(route(router, HttpRequest.GET("/set")).isWebSocketRoute());
    }

    @Test
    void aPlainRouteIsNotAWebSocketRoute() {
        Router router = router(routes -> routes.GET("/plain").handle((request, pathVariables) -> io.micronaut.http.HttpResponse.ok()));
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
