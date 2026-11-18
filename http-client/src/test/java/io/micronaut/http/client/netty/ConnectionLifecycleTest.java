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

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifecycle of a pooled HTTP/1 connection: a connection that winds down takes no further
 * request and closes once the request it runs is done, whichever thread asked for it.
 */
@Timeout(15)
class ConnectionLifecycleTest {

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void housekeepingFromAForeignThreadTakesEffectOnTheLoop(boolean shutdown, boolean leased) throws Exception {
        try (var fixture = new ConnectionFixture()) {
            var handle = fixture.acquire();
            var connection = fixture.connection();
            if (!leased) {
                handle.channel().eventLoop().submit(handle::release).sync();
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
                // nothing closes the connection under the event loop
                assertTrue(handle.channel().isActive());
                assertEquals(leased, connection.hasLiveRequests());
            } finally {
                resume.countDown();
            }
            handle.channel().eventLoop().submit(() -> {
                // refresh lets a running request finish; stopping the client aborts it
                assertEquals(!shutdown && leased, handle.channel().isOpen());
                assertTrue(shutdown || connection.windDownConnection);
            }).sync();
            if (leased) {
                // the close is seen by the connection after the close future completed
                await().atMost(10, TimeUnit.SECONDS).until(() -> !handle.canReturn());
                handle.channel().eventLoop().submit(handle::release).sync();
            }
            handle.channel().closeFuture().sync();
            fixture.awaitUnpooled();
        }
    }

    @Test
    void taintFromAForeignThreadSurvivesAReleaseThatReachesTheLoopFirst() throws Exception {
        try (var fixture = new ConnectionFixture()) {
            var handle = fixture.acquire();
            var connection = fixture.connection();
            CountDownLatch tainted = new CountDownLatch(1);
            // the release is queued on the loop before the taint returns
            var released = handle.channel().eventLoop().submit(() -> {
                assertTrue(tainted.await(10, TimeUnit.SECONDS));
                handle.release();
                return null;
            });
            handle.taint();
            assertFalse(handle.canReturn());
            tainted.countDown();
            released.sync();
            assertTrue(connection.windDownConnection);
            handle.channel().closeFuture().sync();
            fixture.awaitUnpooled();
        }
    }

    @Test
    void aReleasedConnectionServesTheNextRequestAndClosesOnceItIsTainted() throws Exception {
        try (var fixture = new ConnectionFixture()) {
            var first = fixture.acquire();
            var connection = fixture.connection();
            assertTrue(connection.hasLiveRequests());
            assertEquals(1, fixture.manager.liveRequestCount());
            first.channel().eventLoop().submit(first::release).sync();
            assertFalse(connection.hasLiveRequests());
            assertEquals(0, fixture.manager.liveRequestCount());
            assertTrue(first.channel().isActive());

            var second = fixture.acquire();
            assertSame(first.channel(), second.channel());
            assertSame(connection, fixture.connection());
            assertTrue(connection.hasLiveRequests());
            // a handle is released once
            assertThrows(IllegalStateException.class, () -> first.channel().eventLoop().submit(first::release).syncUninterruptibly());
            assertTrue(connection.hasLiveRequests());
            assertTrue(second.channel().isActive());

            second.channel().eventLoop().submit(() -> {
                second.taint();
                assertTrue(second.channel().isActive());
                second.release();
            }).sync();
            second.channel().closeFuture().sync();
            fixture.awaitUnpooled();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aConnectionEstablishedAfterItsPoolWasShutDownIsNotKept(boolean cancelled) throws Exception {
        try (var fixture = new ConnectionFixture()) {
            fixture.manager.stallConnect = true;
            var flow = fixture.manager.connect(new NettyHttpClient.RequestKey(fixture.client.getNettyHttpClient(), fixture.uri), null);
            assertTrue(fixture.manager.connecting.await(10, TimeUnit.SECONDS));
            try {
                if (cancelled) {
                    flow.cancel();
                }
                // the group is not the client's, so it goes on and establishes the connection
                fixture.manager.shutdown();
            } finally {
                fixture.manager.resume.countDown();
            }
            Channel channel = fixture.manager.connected.get();
            if (!cancelled) {
                // the request that waited for the connection still gets it
                var handle = flow.toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertSame(channel, handle.channel());
                channel.eventLoop().submit(() -> {
                    assertTrue(channel.isActive());
                    assertFalse(handle.canReturn());
                    handle.release();
                }).sync();
            }
            // nothing would close a connection kept by a pool that was shut down
            assertTrue(channel.closeFuture().await(5, TimeUnit.SECONDS), "The connection stays open");
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
                request = new NettyClientHttpRequest<String>(HttpMethod.POST, "/") {
                    @Override
                    io.netty.handler.codec.http.HttpRequest toOutgoingHttpRequest(String uri) {
                        throw new IllegalStateException("raw request failure");
                    }
                }.body("test");
            } else {
                request = HttpRequest.POST("/", stage.equals("encoding") ? new BrokenBody() : "test");
            }
            var response = Mono.from(client.exchange(request, String.class)).toFuture();
            server.setSoTimeout(10000);
            try (Socket socket = server.accept()) {
                // the failure reaches the caller, it does not wait for a response
                assertThrows(ExecutionException.class, () -> response.get(10, TimeUnit.SECONDS));
                ConnectionManager manager = client.connectionManager();
                if (stage.equals("pipeline")) {
                    // nothing usable was written: the connection is not kept
                    socket.setSoTimeout(10000);
                    while (socket.getInputStream().read() != -1) {
                        // skip what was written before the failure
                    }
                }
                // the connection is not left marked as busy
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (manager.liveRequestCount() != 0 && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertEquals(0, manager.liveRequestCount());
                for (Channel channel : manager.getChannels()) {
                    channel.eventLoop().submit(() -> { }).sync();
                }
            }
        }
    }

    private static final class BrokenBody implements CharSequence {
        @Override
        public int length() {
            return 1;
        }

        @Override
        public char charAt(int index) {
            return 'x';
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return this;
        }

        @Override
        public String toString() {
            throw new IllegalStateException("encoding failure");
        }
    }

    private static final class CapturingManager extends ConnectionManager {
        final CountDownLatch connecting = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        final AtomicReference<Channel> connected = new AtomicReference<>();
        volatile boolean stallConnect;
        private PoolHolder capturedPool;

        CapturingManager(ConnectionManager from) {
            super(from);
        }

        @Override
        ChannelFuture doConnect(NettyHttpClient.RequestKey requestKey, CustomizerAwareInitializer channelInitializer, EventLoopGroup eventLoop) {
            ChannelFuture future = super.doConnect(requestKey, channelInitializer, eventLoop);
            if (stallConnect) {
                // holds the event loop of the connection, so that it is established only later
                connected.set(future.channel());
                connecting.countDown();
                try {
                    assertTrue(resume.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
            return future;
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

        /**
         * The pool drops a connection once the close reached its handlers, which is after the
         * close future completed.
         */
        void awaitUnpooled() {
            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                var found = new AtomicReference<Object>();
                capturedPoolConnections(found);
                return found.get() == null;
            });
            assertEquals(0, manager.liveRequestCount());
        }

        private void capturedPoolConnections(AtomicReference<Object> found) {
            manager.capturedPool.pool.forEachConnection(found::set);
        }

        @Override
        public void close() throws Exception {
            client.close();
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
            server.close();
        }
    }
}
