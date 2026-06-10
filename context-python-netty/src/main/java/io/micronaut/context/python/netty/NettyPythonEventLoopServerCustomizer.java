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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.python.PythonAsyncioConfiguration;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.server.netty.NettyServerCustomizer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Registers Netty event-loop binding for Micronaut server request processing.
 */
@Singleton
@Requires(classes = NettyServerCustomizer.class)
@Requires(property = PythonAsyncioConfiguration.ENABLED, notEquals = "false")
final class NettyPythonEventLoopServerCustomizer implements BeanCreatedEventListener<NettyServerCustomizer.Registry> {
    static final String HANDLER_NAME = "python-asyncio-event-loop";

    @Override
    public NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
        NettyServerCustomizer.Registry registry = event.getBean();
        registry.register(binderCustomizer());
        return registry;
    }

    static NettyServerCustomizer binderCustomizer() {
        return new BinderCustomizer(null);
    }

    private record BinderCustomizer(@Nullable Channel channel) implements NettyServerCustomizer {

        @Override
        public NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            return switch (role) {
                case CONNECTION, REQUEST_STREAM -> new BinderCustomizer(channel);
                case LISTENER, PUSH_PROMISE_STREAM -> this;
            };
        }

        @Override
        public void onStreamPipelineBuilt() {
            if (channel == null || channel.pipeline().get(HANDLER_NAME) != null) {
                return;
            }
            if (channel.pipeline().get(ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND) == null) {
                return;
            }
            channel.pipeline().addBefore(
                ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND,
                HANDLER_NAME,
                NettyPythonEventLoopBindingHandler.INSTANCE
            );
        }
    }

    @ChannelHandler.Sharable
    private static final class NettyPythonEventLoopBindingHandler extends ChannelInboundHandlerAdapter {
        private static final NettyPythonEventLoopBindingHandler INSTANCE = new NettyPythonEventLoopBindingHandler();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try (NettyPythonEventLoopProvider.Scope ignored = NettyPythonEventLoopProvider.bind(ctx.channel().eventLoop())) {
                super.channelRead(ctx, msg);
            }
        }
    }
}
