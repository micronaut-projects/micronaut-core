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
package io.micronaut.context.python.netty;

import io.micronaut.context.python.PythonEventLoop;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.server.netty.NettyServerCustomizer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.resolver.AddressResolverGroup;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NettyPythonEventLoopProviderTest {

    @Test
    void exposesBoundNettyEventLoopForCurrentThread() {
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        try {
            NettyPythonEventLoopProvider provider = new NettyPythonEventLoopProvider();
            assertFalse(provider.current().isPresent());

            try (NettyPythonEventLoopProvider.Scope ignored = NettyPythonEventLoopProvider.bind(eventLoop)) {
                Optional<PythonEventLoop> current = provider.current();
                assertTrue(current.isPresent());
                assertFalse(current.get().inEventLoop());
            }

            assertFalse(provider.current().isPresent());
        } finally {
            eventLoop.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void schedulesCallbacksOnNettyEventLoop() throws Exception {
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            NettyPythonEventLoop pythonEventLoop = new NettyPythonEventLoop(eventLoop);

            java.util.concurrent.ScheduledFuture<?> ignored = pythonEventLoop.schedule(latch::countDown, 1, TimeUnit.MILLISECONDS);

            assertTrue(latch.await(1, TimeUnit.SECONDS));
        } finally {
            eventLoop.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void cancelsScheduledCallbacksOnNettyEventLoop() throws Exception {
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        try {
            AtomicBoolean invoked = new AtomicBoolean();
            NettyPythonEventLoop pythonEventLoop = new NettyPythonEventLoop(eventLoop);

            ScheduledFuture<?> future = pythonEventLoop.schedule(() -> invoked.set(true), 50, TimeUnit.MILLISECONDS);
            assertTrue(future.cancel(false));

            Thread.sleep(100);
            assertFalse(invoked.get());
        } finally {
            eventLoop.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void cachesResolverGroupPerEventLoop() {
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        try {
            NettyPythonEventLoopSupport support = new NettyPythonEventLoopSupport();

            AddressResolverGroup<InetSocketAddress> first = support.resolver(eventLoop);
            AddressResolverGroup<InetSocketAddress> second = support.resolver(eventLoop);

            assertSame(first, second);
            support.closeAll().toCompletableFuture().join();
        } finally {
            eventLoop.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void reusePortRequiresNativeEventLoopSupport() {
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        try {
            NettyPythonEventLoopSupport support = new NettyPythonEventLoopSupport();

            assertNull(support.reusePortOption(eventLoop));
        } finally {
            eventLoop.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void serverCustomizerBindsEventLoopAroundMicronautInboundHandler() {
        NettyPythonEventLoopProvider provider = new NettyPythonEventLoopProvider();
        AtomicBoolean sawBoundEventLoop = new AtomicBoolean();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                Optional<PythonEventLoop> current = provider.current();
                sawBoundEventLoop.set(current.isPresent() && current.get() instanceof NettyPythonEventLoop nettyLoop && nettyLoop.eventLoop() == ctx.channel().eventLoop());
            }
        });

        NettyServerCustomizer customizer = NettyPythonEventLoopServerCustomizer.binderCustomizer()
            .specializeForChannel(channel, NettyServerCustomizer.ChannelRole.CONNECTION);
        customizer.onStreamPipelineBuilt();

        assertNotNull(channel.pipeline().get(NettyPythonEventLoopServerCustomizer.HANDLER_NAME));

        channel.writeInbound("request");

        assertTrue(sawBoundEventLoop.get());
        assertFalse(provider.current().isPresent());
        channel.finishAndReleaseAll();
    }
}
