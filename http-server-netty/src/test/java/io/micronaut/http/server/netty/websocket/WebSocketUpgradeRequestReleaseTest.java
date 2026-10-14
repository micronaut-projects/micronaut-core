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
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.LifecycleHttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.runtime.http.scope.RequestScope;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.micronaut.websocket.event.WebSocketSessionClosedEvent;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WebSocket upgrade request is released like any other request: once the session it opened
 * ended, or once the response that rejected the upgrade was written. Releasing it runs its
 * disposal resources, publishes the {@link HttpRequestTerminatedEvent} and so destroys its
 * request scoped beans.
 */
class WebSocketUpgradeRequestReleaseTest {
    private static final String SPEC = "WebSocketUpgradeRequestReleaseTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        client.close();
        server.close();
        ctx.close();
    }

    @BeforeEach
    void reset() {
        ctx.getBean(Recorder.class).clear();
    }

    private static URI uri(String path) {
        return URI.create("ws://localhost:" + server.getPort() + path);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met in time");
            }
            Thread.sleep(10);
        }
    }

    @Test
    void upgradeRequestIsReleasedWhenTheSessionEnds() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        CompletableFuture<String> received = new CompletableFuture<>();
        WebSocket webSocket = client.newWebSocketBuilder().buildAsync(uri("/ws-release/echo"), new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                received.complete(data.toString());
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }
        }).get(10, TimeUnit.SECONDS);

        webSocket.sendText("hello", true).get(10, TimeUnit.SECONDS);
        assertEquals("hello", received.get(10, TimeUnit.SECONDS));

        // the session uses the request until it ends
        assertTrue(recorder.disposed.isEmpty());
        assertTrue(recorder.terminated.isEmpty());
        assertTrue(recorder.destroyedBeans.isEmpty());

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(10, TimeUnit.SECONDS);

        await(() -> !recorder.disposed.isEmpty() && !recorder.terminated.isEmpty() && !recorder.destroyedBeans.isEmpty());
        assertEquals(List.of("/ws-release/echo"), recorder.disposed);
        assertEquals(List.of("/ws-release/echo"), recorder.terminated);
        assertEquals(List.of("/ws-release/echo"), recorder.destroyedBeans);
        // the @OnClose handler still had the request
        assertEquals(List.of(Boolean.FALSE), recorder.disposedAtClose);
    }

    @Test
    void upgradeRequestIsReleasedWhenTheConnectionIsLost() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        WebSocket webSocket = client.newWebSocketBuilder().buildAsync(uri("/ws-release/echo"), new WebSocket.Listener() {
        }).get(10, TimeUnit.SECONDS);
        await(() -> !recorder.opened.isEmpty());

        webSocket.abort();

        await(() -> !recorder.disposed.isEmpty() && !recorder.terminated.isEmpty());
        assertEquals(List.of("/ws-release/echo"), recorder.disposed);
        assertEquals(List.of("/ws-release/echo"), recorder.terminated);
    }

    @Test
    void upgradeRequestIsReleasedOnceAnAsynchronousOnCloseCompleted() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        WebSocket webSocket = client.newWebSocketBuilder().buildAsync(uri("/ws-release-slow/close"), new WebSocket.Listener() {
        }).get(10, TimeUnit.SECONDS);
        await(() -> !recorder.opened.isEmpty());

        // the server invokes @OnClose as the connection goes away
        webSocket.abort();

        await(() -> !recorder.disposed.isEmpty() && !recorder.terminated.isEmpty());
        assertEquals(List.of(Boolean.FALSE), recorder.disposedAtClose);
        assertEquals(List.of("/ws-release-slow/close"), recorder.disposed);
        assertEquals(List.of("/ws-release-slow/close"), recorder.terminated);
    }

    @Test
    void upgradeRequestIsReleasedOnceAnEarlierAsynchronousOnCloseCompleted() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        WebSocket webSocket = client.newWebSocketBuilder().buildAsync(uri("/ws-release-blocked/close"), new WebSocket.Listener() {
        }).get(10, TimeUnit.SECONDS);
        await(() -> !recorder.opened.isEmpty());

        // the close frame invokes @OnClose, then the connection goes away while it still runs
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(10, TimeUnit.SECONDS);
        assertTrue(recorder.handlerEntered.await(10, TimeUnit.SECONDS));
        webSocket.abort();
        await(() -> !recorder.sessionsClosed.isEmpty());
        Thread.sleep(200);

        // the @OnClose handler still uses the request
        assertTrue(recorder.disposed.isEmpty());
        assertTrue(recorder.terminated.isEmpty());

        recorder.releaseHandler.countDown();
        await(() -> !recorder.disposed.isEmpty() && !recorder.terminated.isEmpty());
        assertEquals(List.of(Boolean.FALSE), recorder.disposedAtClose);
        assertEquals(List.of("/ws-release-blocked/close"), recorder.disposed);
        assertEquals(List.of("/ws-release-blocked/close"), recorder.terminated);
    }

    @Test
    void upgradeRequestIsReleasedOnceAnAsynchronousOnMessageCompleted() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        WebSocket webSocket = client.newWebSocketBuilder().buildAsync(uri("/ws-release-blocked/message"), new WebSocket.Listener() {
        }).get(10, TimeUnit.SECONDS);
        await(() -> !recorder.opened.isEmpty());

        // the connection goes away while @OnMessage still runs
        webSocket.sendText("hello", true).get(10, TimeUnit.SECONDS);
        assertTrue(recorder.handlerEntered.await(10, TimeUnit.SECONDS));
        webSocket.abort();
        await(() -> !recorder.sessionsClosed.isEmpty());
        Thread.sleep(200);

        // the @OnMessage handler still uses the request and its request scoped bean
        assertTrue(recorder.disposed.isEmpty());
        assertTrue(recorder.destroyedBeans.isEmpty());

        recorder.releaseHandler.countDown();
        await(() -> !recorder.disposed.isEmpty() && !recorder.terminated.isEmpty() && !recorder.destroyedBeans.isEmpty());
        // the resumed handler used the bean of the request, destroyed once it completed
        assertEquals(recorder.beanAtOpen, recorder.beanAtMessage);
        assertEquals(List.of(Boolean.FALSE), recorder.disposedAtMessage);
        assertEquals(List.of("/ws-release-blocked/message"), recorder.disposed);
        assertEquals(List.of("/ws-release-blocked/message"), recorder.destroyedBeans);
        assertEquals(1, recorder.createdBeans.get());
    }

    @Test
    void rejectedUpgradeRequestIsReleased() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        ExecutionException e = assertThrows(ExecutionException.class, () -> client.newWebSocketBuilder()
            .buildAsync(uri("/ws-release/forbidden"), new WebSocket.Listener() {
            }).get(10, TimeUnit.SECONDS));
        WebSocketHandshakeException handshakeException = assertInstanceOf(WebSocketHandshakeException.class, e.getCause());
        assertEquals(HttpStatus.FORBIDDEN.getCode(), handshakeException.getResponse().statusCode());

        await(() -> !recorder.disposed.isEmpty() && !recorder.terminated.isEmpty());
        assertEquals(List.of("/ws-release/forbidden"), recorder.disposed);
        assertEquals(List.of("/ws-release/forbidden"), recorder.terminated);
        assertTrue(recorder.opened.isEmpty());
    }

    @Test
    void upgradeRequestFilteredOffTheEventLoopIsReleasedWhenTheSessionEnds() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        WebSocket webSocket = client.newWebSocketBuilder().buildAsync(uri("/ws-release/offloop"), new WebSocket.Listener() {
        }).get(10, TimeUnit.SECONDS);
        await(() -> !recorder.opened.isEmpty());

        // the filter completed the upgrade off the event loop, which then wrote the response
        assertEquals(List.of(Boolean.FALSE), recorder.filteredOnEventLoop);
        assertTrue(recorder.disposed.isEmpty());

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(10, TimeUnit.SECONDS);

        await(() -> !recorder.disposed.isEmpty() && !recorder.terminated.isEmpty());
        assertEquals(List.of("/ws-release/offloop"), recorder.disposed);
        assertEquals(List.of("/ws-release/offloop"), recorder.terminated);
    }

    @Test
    void upgradeRequestOfAnUnsupportedVersionIsReleased() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        try (Socket socket = new Socket("localhost", server.getPort())) {
            // no session opens: the handshake answers the versions it supports
            String statusLine = sendUpgradeRequest(socket, "/ws-release/unsupported",
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                "Sec-WebSocket-Version: 99\r\n");
            assertTrue(statusLine.startsWith("HTTP/1.1 426"), statusLine);

            await(() -> !recorder.disposed.isEmpty() && !recorder.terminated.isEmpty());
        }
        assertEquals(List.of("/ws-release/unsupported"), recorder.disposed);
        assertEquals(List.of("/ws-release/unsupported"), recorder.terminated);
        assertTrue(recorder.opened.isEmpty());
    }

    @Test
    void upgradeRequestWhoseHandshakeFailsIsReleased() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        try (Socket socket = new Socket("localhost", server.getPort())) {
            // the handshake fails on the missing key
            OutputStream out = socket.getOutputStream();
            out.write(upgradeRequest("/ws-release/nokey", "Sec-WebSocket-Version: 13\r\n"));
            out.flush();

            await(() -> !recorder.disposed.isEmpty() && !recorder.terminated.isEmpty());
        }
        assertEquals(List.of("/ws-release/nokey"), recorder.disposed);
        assertEquals(List.of("/ws-release/nokey"), recorder.terminated);
        assertTrue(recorder.opened.isEmpty());
    }

    private static byte[] upgradeRequest(String path, String headers) {
        return ("GET " + path + " HTTP/1.1\r\n" +
            "Host: localhost:" + server.getPort() + "\r\n" +
            "Connection: Upgrade\r\n" +
            "Upgrade: websocket\r\n" +
            headers +
            "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static String sendUpgradeRequest(Socket socket, String path, String headers) throws Exception {
        socket.setSoTimeout(10_000);
        OutputStream out = socket.getOutputStream();
        out.write(upgradeRequest(path, headers));
        out.flush();
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        return in.readLine();
    }

    @Test
    void notFoundUpgradeRequestIsReleased() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        ExecutionException e = assertThrows(ExecutionException.class, () -> client.newWebSocketBuilder()
            .buildAsync(uri("/ws-missing"), new WebSocket.Listener() {
            }).get(10, TimeUnit.SECONDS));
        WebSocketHandshakeException handshakeException = assertInstanceOf(WebSocketHandshakeException.class, e.getCause());
        assertEquals(HttpStatus.NOT_FOUND.getCode(), handshakeException.getResponse().statusCode());

        await(() -> !recorder.terminated.isEmpty());
        assertEquals(List.of("/ws-missing"), recorder.terminated);
        assertFalse(recorder.terminated.contains("/ws-release/echo"));
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    static class Recorder {
        final List<String> opened = new CopyOnWriteArrayList<>();
        final List<String> disposed = new CopyOnWriteArrayList<>();
        final List<String> terminated = new CopyOnWriteArrayList<>();
        final List<String> destroyedBeans = new CopyOnWriteArrayList<>();
        final List<Boolean> disposedAtClose = new CopyOnWriteArrayList<>();
        final List<Boolean> disposedAtMessage = new CopyOnWriteArrayList<>();
        final List<String> sessionsClosed = new CopyOnWriteArrayList<>();
        final List<Integer> beanAtOpen = new CopyOnWriteArrayList<>();
        final List<Integer> beanAtMessage = new CopyOnWriteArrayList<>();
        final List<Boolean> filteredOnEventLoop = new CopyOnWriteArrayList<>();
        final AtomicInteger createdBeans = new AtomicInteger();
        volatile CountDownLatch handlerEntered = new CountDownLatch(1);
        volatile CountDownLatch releaseHandler = new CountDownLatch(1);

        void clear() {
            opened.clear();
            disposed.clear();
            terminated.clear();
            destroyedBeans.clear();
            disposedAtClose.clear();
            disposedAtMessage.clear();
            sessionsClosed.clear();
            beanAtOpen.clear();
            beanAtMessage.clear();
            filteredOnEventLoop.clear();
            createdBeans.set(0);
            handlerEntered = new CountDownLatch(1);
            releaseHandler = new CountDownLatch(1);
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @ServerFilter({"/ws-release/**", "/ws-release-slow/**", "/ws-release-blocked/**"})
    static class DisposalFilter {
        private final Recorder recorder;

        DisposalFilter(Recorder recorder) {
            this.recorder = recorder;
        }

        @RequestFilter
        @Nullable
        HttpResponse<?> filter(HttpRequest<?> request) {
            HttpRequest<?> current = request;
            while (current instanceof HttpRequestWrapper<?> wrapper && !(current instanceof LifecycleHttpRequest<?>)) {
                current = wrapper.getDelegate();
            }
            String path = request.getPath();
            ((LifecycleHttpRequest<?>) current).addDisposalResource(() -> recorder.disposed.add(path));
            if (path.endsWith("/forbidden")) {
                return HttpResponse.status(HttpStatus.FORBIDDEN);
            }
            return null;
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @ServerFilter("/ws-release/offloop")
    static class OffEventLoopFilter {
        private final Recorder recorder;

        OffEventLoopFilter(Recorder recorder) {
            this.recorder = recorder;
        }

        @RequestFilter
        @ExecuteOn(TaskExecutors.BLOCKING)
        void filter(HttpRequest<?> request) {
            HttpRequest<?> current = request;
            while (current instanceof HttpRequestWrapper<?> wrapper && !(current instanceof NettyHttpRequest<?>)) {
                current = wrapper.getDelegate();
            }
            recorder.filteredOnEventLoop.add(((NettyHttpRequest<?>) current).getChannelHandlerContext().executor().inEventLoop());
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    static class TerminatedListener implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        private final Recorder recorder;

        TerminatedListener(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public void onApplicationEvent(HttpRequestTerminatedEvent event) {
            recorder.terminated.add(event.getSource().getPath());
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    static class SessionClosedListener implements ApplicationEventListener<WebSocketSessionClosedEvent> {
        private final Recorder recorder;

        SessionClosedListener(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public void onApplicationEvent(WebSocketSessionClosedEvent event) {
            recorder.sessionsClosed.add(event.getSource().getId());
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @RequestScope
    static class RequestBean {
        private final Recorder recorder;
        private final int id;
        private String path;

        RequestBean(Recorder recorder) {
            this.recorder = recorder;
            this.id = recorder.createdBeans.incrementAndGet();
        }

        void use(String path) {
            this.path = path;
        }

        int id() {
            return id;
        }

        @PreDestroy
        void destroy() {
            recorder.destroyedBeans.add(path);
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @ServerWebSocket("/ws-release/{name}")
    static class ReleaseSocket {
        private final Recorder recorder;
        private final RequestBean requestBean;

        ReleaseSocket(Recorder recorder, RequestBean requestBean) {
            this.recorder = recorder;
            this.requestBean = requestBean;
        }

        @OnOpen
        void onOpen(String name, HttpRequest<?> request) {
            requestBean.use(request.getPath());
            recorder.opened.add(request.getPath());
        }

        @OnMessage
        Publisher<String> onMessage(String message, WebSocketSession session) {
            return session.send(message);
        }

        @OnClose
        void onClose(String name, WebSocketSession session) {
            recorder.disposedAtClose.add(!recorder.disposed.isEmpty());
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @ServerWebSocket("/ws-release-slow/{name}")
    static class SlowCloseSocket {
        private final Recorder recorder;

        SlowCloseSocket(Recorder recorder) {
            this.recorder = recorder;
        }

        @OnOpen
        void onOpen(String name, HttpRequest<?> request) {
            recorder.opened.add(request.getPath());
        }

        @OnMessage
        void onMessage(String message) {
            // the messages are not used: the socket only tests the release once @OnClose completed
        }

        @OnClose
        @ExecuteOn(TaskExecutors.BLOCKING)
        void onClose(String name) throws InterruptedException {
            Thread.sleep(200);
            recorder.disposedAtClose.add(!recorder.disposed.isEmpty());
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @ServerWebSocket("/ws-release-blocked/{name}")
    static class BlockedSocket {
        private final Recorder recorder;
        private final RequestBean requestBean;

        BlockedSocket(Recorder recorder, RequestBean requestBean) {
            this.recorder = recorder;
            this.requestBean = requestBean;
        }

        @OnOpen
        void onOpen(String name, HttpRequest<?> request) {
            requestBean.use(request.getPath());
            recorder.beanAtOpen.add(requestBean.id());
            recorder.opened.add(request.getPath());
        }

        @OnMessage
        @ExecuteOn(TaskExecutors.BLOCKING)
        void onMessage(String message) throws InterruptedException {
            recorder.handlerEntered.countDown();
            recorder.releaseHandler.await(10, TimeUnit.SECONDS);
            recorder.disposedAtMessage.add(!recorder.disposed.isEmpty());
            recorder.beanAtMessage.add(requestBean.id());
        }

        @OnClose
        @ExecuteOn(TaskExecutors.BLOCKING)
        void onClose(String name) throws InterruptedException {
            if (name.equals("close")) {
                recorder.handlerEntered.countDown();
                recorder.releaseHandler.await(10, TimeUnit.SECONDS);
                recorder.disposedAtClose.add(!recorder.disposed.isEmpty());
            }
        }
    }
}
