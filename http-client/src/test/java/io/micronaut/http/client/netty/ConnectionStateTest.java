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
package io.micronaut.http.client.netty;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.micronaut.http.client.netty.ConnectionManager.PoolHolder.ConnectionState.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class ConnectionStateTest {
    enum Operation { LEASE, RELEASE, DRAIN, CLOSED }

    static Stream<Arguments> transitions() {
        return Stream.of(false, true).flatMap(http2 -> Stream.of(ConnectionManager.PoolHolder.ConnectionState.values())
            .flatMap(state -> Stream.of(Operation.values()).map(operation -> Arguments.of(http2, state, operation))));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void everyStateAndOperation(boolean http2, ConnectionManager.PoolHolder.ConnectionState initial, Operation operation) {
        try (var client = DefaultHttpClient.builder().build()) {
            var channel = new EmbeddedChannel();
            try {
                var pool = client.connectionManager().createPool(
                    new NettyHttpClient.RequestKey(client.getNettyHttpClient(), URI.create("http://localhost")), List.of(channel.eventLoop()));
                ConnectionManager.PoolHolder.ConnectionHolder connection = http2
                    ? pool.new Http2ConnectionHolder(channel, new NettyClientCustomizer() { })
                    : pool.new Http1ConnectionHolder(channel, new NettyClientCustomizer() { });
                if (initial != HANDSHAKING) {
                    connection.release();
                }
                if (initial == LEASED || initial == DRAINING) {
                    assertTrue(connection.lease());
                }
                if (initial == DRAINING) {
                    connection.drain();
                }
                if (initial == CLOSED) {
                    channel.close();
                }
                assertEquals(initial, connection.state);

                var expected = switch (operation) {
                    case LEASE -> initial == IDLE || initial == LEASED ? LEASED : initial;
                    case RELEASE -> switch (initial) {
                        case HANDSHAKING, IDLE, LEASED -> IDLE;
                        case DRAINING, CLOSED -> CLOSED;
                    };
                    case DRAIN -> switch (initial) {
                        case HANDSHAKING, IDLE, CLOSED -> CLOSED;
                        case LEASED, DRAINING -> DRAINING;
                    };
                    case CLOSED -> CLOSED;
                };
                switch (operation) {
                    case LEASE -> assertEquals(initial == IDLE || (http2 && initial == LEASED), connection.lease());
                    case RELEASE -> connection.release();
                    case DRAIN -> connection.drain();
                    case CLOSED -> channel.close();
                }
                assertEquals(expected, connection.state);
                int expectedCount = expected == LEASED || expected == DRAINING ? 1 : 0;
                if (http2 && initial == LEASED && operation == Operation.LEASE) {
                    expectedCount = 2;
                }
                assertEquals(expectedCount, connection.liveRequestCount());
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    @Test
    void http2DrainsOnlyAfterItsLastStream() {
        try (var client = DefaultHttpClient.builder().build()) {
            var channel = new EmbeddedChannel();
            try {
                var pool = client.connectionManager().createPool(
                    new NettyHttpClient.RequestKey(client.getNettyHttpClient(), URI.create("http://localhost")), List.of(channel.eventLoop()));
                var connection = pool.new Http2ConnectionHolder(channel, new NettyClientCustomizer() { });
                connection.release();
                assertTrue(connection.lease());
                assertTrue(connection.lease());
                connection.release();
                assertEquals(LEASED, connection.state);
                assertTrue(connection.lease());
                connection.drain();
                connection.release();
                assertEquals(DRAINING, connection.state);
                assertEquals(1, connection.liveRequestCount());
                assertTrue(channel.isActive());
                assertFalse(connection.lease());
                connection.release();
                assertEquals(CLOSED, connection.state);
                assertEquals(0, connection.liveRequestCount());
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    static Stream<Arguments> housekeeping() {
        return Stream.of(false, true).flatMap(shutdown -> Stream.of(false, true).map(leased -> Arguments.of(shutdown, leased)));
    }

    @ParameterizedTest
    @MethodSource("housekeeping")
    void housekeepingFromAForeignThreadRunsOnTheLoop(boolean shutdown, boolean leased) throws Exception {
        try (var fixture = new ConnectionFixture()) {
            var handle = fixture.acquire();
            var connection = fixture.connection();
            if (!leased) {
                handle.release();
                handle.channel().eventLoop().submit(() -> { }).sync();
            }
            CountDownLatch blocked = new CountDownLatch(1);
            CountDownLatch resume = new CountDownLatch(1);
            handle.channel().eventLoop().execute(() -> {
                blocked.countDown();
                try {
                    assertTrue(resume.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            });
            assertTrue(blocked.await(10, TimeUnit.SECONDS));
            try {
                if (shutdown) {
                    fixture.manager.shutdown();
                } else {
                    fixture.manager.refresh();
                }
                assertEquals(leased ? LEASED : IDLE, connection.state);
            } finally {
                resume.countDown();
            }
            handle.channel().eventLoop().submit(() -> {
                // refresh lets a running request finish; stopping the client aborts it
                assertEquals(leased && !shutdown ? DRAINING : CLOSED, connection.state);
                assertFalse(connection.lease());
            }).sync();
            if (leased) {
                handle.release();
            }
            handle.channel().closeFuture().sync();
            handle.channel().eventLoop().submit(() -> assertEquals(CLOSED, connection.state)).sync();
        }
    }

    @Test
    void doubleReleaseCannotReleaseTheNextLease() throws Exception {
        try (var fixture = new ConnectionFixture()) {
            var first = fixture.acquire();
            var connection = fixture.connection();
            first.release();
            first.channel().eventLoop().submit(() -> { }).sync();
            var second = fixture.acquire();
            assertSame(first.channel(), second.channel());
            first.release();
            first.channel().eventLoop().submit(() -> {
                assertEquals(LEASED, connection.state);
                assertEquals(1, connection.liveRequestCount());
            }).sync();
            second.release();
            second.release();
            second.channel().eventLoop().submit(() -> {
                assertEquals(IDLE, connection.state);
                assertEquals(0, connection.liveRequestCount());
            }).sync();
        }
    }

    @Test
    void doubleReleaseOfAHttp2HandleDoesNotChangeTheStreamCount() throws Exception {
        try (var client = DefaultHttpClient.builder().build()) {
            var channel = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(),
                new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
            try {
                var pool = client.connectionManager().createPool(
                    new NettyHttpClient.RequestKey(client.getNettyHttpClient(), URI.create("http://localhost")), List.of(channel.eventLoop()));
                var connection = pool.new Http2ConnectionHolder(channel, new NettyClientCustomizer() { });
                connection.release();
                var firstRequest = pool.pool.createPendingRequest(null);
                connection.dispatch0(firstRequest);
                var first = firstRequest.flow().toCompletableFuture().get(10, TimeUnit.SECONDS);
                var secondRequest = pool.pool.createPendingRequest(null);
                connection.dispatch0(secondRequest);
                var second = secondRequest.flow().toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertEquals(2, connection.liveRequestCount());
                first.release();
                first.release();
                assertEquals(1, connection.liveRequestCount());
                assertEquals(LEASED, connection.state);
                connection.drain();
                first.release();
                assertEquals(DRAINING, connection.state);
                assertTrue(channel.isActive());
                second.release();
                second.release();
                assertEquals(CLOSED, connection.state);
                assertEquals(0, connection.liveRequestCount());
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    @Test
    void poolEntriesRequireTheirEventLoop() throws Exception {
        try (var fixture = new ConnectionFixture()) {
            var handle = fixture.acquire();
            var pool = fixture.manager.capturedPool.pool;
            var connection = fixture.connection();
            var http1 = pool.createHttp1PoolEntry(handle.channel().eventLoop(), connection);
            assertThrows(AssertionError.class, http1::onConnectionEstablished);
            assertThrows(AssertionError.class, http1::onConnectionInactive);
            assertThrows(AssertionError.class, http1::markAvailable);
            assertThrows(AssertionError.class, http1::markUnavailable);
            var http2 = pool.createHttp2PoolEntry(handle.channel().eventLoop(), connection);
            assertThrows(AssertionError.class, () -> http2.onConnectionEstablished(2));
            assertThrows(AssertionError.class, http2::onConnectionInactive);
            assertThrows(AssertionError.class, http2::markAvailable);
            assertThrows(AssertionError.class, http2::markUnavailable);
            handle.release();
        }
    }

    @Test
    void stateMutatorsRequireTheirEventLoop() throws Exception {
        try (var fixture = new ConnectionFixture()) {
            var handle = fixture.acquire();
            var connection = fixture.connection();
            assertThrows(AssertionError.class, connection::lease);
            assertThrows(AssertionError.class, connection::release);
            assertThrows(AssertionError.class, connection::drain);
            assertThrows(AssertionError.class, connection::closed);
            handle.release();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"encoding", "raw-request", "pipeline"})
    void setupFailureReleasesTheHandle(String stage) throws Exception {
        NettyClientCustomizer customizer = new NettyClientCustomizer() {
            @Override
            public void onRequestPipelineBuilt() {
                if (stage.equals("pipeline")) {
                    throw new IllegalStateException("pipeline failure");
                }
            }
        };
        try (var server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
             var client = DefaultHttpClient.builder().uri(URI.create("http://127.0.0.1:" + server.getLocalPort()))
                 .clientCustomizer(customizer).build()) {
            MutableHttpRequest<?> request;
            if (stage.equals("raw-request")) {
                request = new NettyClientHttpRequest<String>(io.micronaut.http.HttpMethod.POST, "/") {
                    @Override
                    public io.netty.handler.codec.http.HttpRequest toHttpRequestWithoutBody() {
                        throw new IllegalStateException("raw request failure");
                    }
                }.body("test");
            } else {
                request = HttpRequest.POST("/", stage.equals("encoding") ? new BrokenBody() : "test");
            }
            var response = Mono.from(client.exchange(request, String.class)).toFuture();
            server.setSoTimeout(10000);
            try (Socket socket = server.accept()) {
                assertThrows(Exception.class, () -> response.get(10, TimeUnit.SECONDS));
                Channel channel = request.getAttribute(NettyClientHttpRequest.CHANNEL, Channel.class).orElseThrow();
                channel.closeFuture().sync();
                channel.eventLoop().submit(() -> { }).sync();
                assertEquals(0, client.connectionManager().liveRequestCount());
                socket.setSoTimeout(10000);
                assertEquals(-1, socket.getInputStream().read());
            }
        }
    }

    private static final class BrokenBody implements CharSequence {
        @Override public int length() { return 1; }
        @Override public char charAt(int index) { return 'x'; }
        @Override public CharSequence subSequence(int start, int end) { return this; }
        @Override public String toString() { throw new IllegalStateException("encoding failure"); }
    }

    private static final class CapturingManager extends ConnectionManager {
        private PoolHolder capturedPool;

        CapturingManager(ConnectionManager from) {
            super(from);
        }

        @Override
        PoolHolder createPool(NettyHttpClient.RequestKey key, Iterable<? extends EventExecutor> group) {
            capturedPool = super.createPool(key, group);
            return capturedPool;
        }
    }

    private static final class ConnectionFixture implements AutoCloseable {
        private final MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        private final ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final URI uri = URI.create("http://127.0.0.1:" + server.getLocalPort());
        private final DefaultHttpClient client = DefaultHttpClient.builder().uri(uri).eventLoopGroup(group).build();
        private final CapturingManager manager = new CapturingManager(client.connectionManager());

        ConnectionFixture() throws Exception {
            client.setConnectionManager(manager);
        }

        ConnectionManager.PoolHandle acquire() throws Exception {
            return manager.connect(new NettyHttpClient.RequestKey(client.getNettyHttpClient(), uri), null)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);
        }

        ConnectionManager.PoolHolder.ConnectionHolder connection() {
            var found = new AtomicReference<ConnectionManager.PoolHolder.ConnectionHolder>();
            manager.capturedPool.pool.forEachConnection(c -> found.set((ConnectionManager.PoolHolder.ConnectionHolder) c));
            return found.get();
        }

        @Override
        public void close() throws Exception {
            client.close();
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
            server.close();
        }
    }
}
