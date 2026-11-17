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

import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pool49ConfinementTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @Timeout(10)
    void cancelledWaiterAbsorbsItsConnectFailure(int cancelledIndex) {
        var configuration = new DefaultHttpClientConfiguration();
        configuration.getConnectionPoolConfiguration().setMaxPendingConnections(4);
        configuration.getConnectionPoolConfiguration().setMaxConcurrentHttp2Connections(4);
        try (var client = DefaultHttpClient.builder().configuration(configuration).build()) {
            var channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
            List<ChannelPromise> attempts = new ArrayList<>();
            var manager = new ConnectionManager(client.connectionManager()) {
                @Override
                ChannelFuture doConnect(NettyHttpClient.RequestKey key, CustomizerAwareInitializer initializer, EventLoopGroup group) {
                    ChannelPromise promise = channel.newPromise();
                    attempts.add(promise);
                    return promise;
                }
            };
            var holder = manager.createPool(new NettyHttpClient.RequestKey(client.getNettyHttpClient(), URI.create("http://localhost")),
                List.of(channel.eventLoop()));
            try {
                List<Pool.PendingRequest> requests = new ArrayList<>();
                for (int i = 0; i < cancelledIndex + 2; i++) {
                    var request = holder.pool.createPendingRequest(null);
                    requests.add(request);
                    request.dispatch();
                }
                assertEquals(requests.size(), attempts.size());
                var last = requests.getLast().flow().toCompletableFuture();
                ((DelayedExecutionFlow<?>) requests.get(cancelledIndex).flow()).cancel();
                for (int i = 0; i < cancelledIndex; i++) {
                    attempts.get(i).setFailure(new IllegalStateException("earlier connect failed"));
                    assertTrue(requests.get(i).flow().toCompletableFuture().isCompletedExceptionally());
                }
                attempts.get(cancelledIndex).setFailure(new IllegalStateException("cancelled connect failed"));
                assertFalse(last.isDone());
                assertEquals(requests.size(), attempts.size());

                attempts.getLast().setSuccess();
                channel.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC, new HttpClientCodec());
                var connection = holder.new Http1ConnectionHolder(channel, new NettyClientCustomizer() { });
                connection.init(false);
                assertSame(channel, last.join().channel());
                last.join().release();
            } finally {
                holder.pool.close();
                channel.finishAndReleaseAll();
            }
        }
    }

    @Test
    @Timeout(10)
    void http1AvailabilityCanOnlyBeRemovedOnItsEventLoop() throws InterruptedException {
        var group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        URI uri = URI.create("http://localhost");
        try (var client = DefaultHttpClient.builder().uri(uri).build()) {
            EventLoop loop = group.next();
            var channel = new NioSocketChannel();
            loop.register(channel).sync();
            try {
                var holder = client.connectionManager().createPool(new NettyHttpClient.RequestKey(client.getNettyHttpClient(), uri), group);
                var connection = holder.new Http1ConnectionHolder(channel, new NettyClientCustomizer() { });
                Pool.Http1PoolEntry entry = holder.pool.createHttp1PoolEntry(loop, connection);

                assertThrows(AssertionError.class, entry::markUnavailable);
                loop.submit(entry::markUnavailable).sync();

                assertThrows(AssertionError.class, connection::drain);
                loop.submit(connection::drain).sync();
                channel.closeFuture().sync();
                assertEquals(ConnectionManager.PoolHolder.ConnectionState.CLOSED, connection.state);
            } finally {
                channel.close().sync();
            }
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }
}
