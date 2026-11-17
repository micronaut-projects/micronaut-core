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

import io.netty.channel.EventLoop;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Pool49ConfinementTest {
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
