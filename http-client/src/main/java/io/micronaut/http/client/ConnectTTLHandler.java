/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

/**
 * A handler that will close channels after they have reached their time-to-live, regardless of usage.
 *
 * channels that are in use will be closed when they are next
 * released to the underlying connection pool.
 */
public class ConnectTTLHandler extends ChannelDuplexHandler {

    public static final AttributeKey<Boolean> RELEASE_CHANNEL = AttributeKey.newInstance("release_channel");

    private final Long connectionTtlMillis;
    private ScheduledFuture<?> channelKiller;

    /**
     * Construct ConnectTTLHandler for given arguments.
     * @param connectionTtlMillis The configured connect-ttl
     */
    public ConnectTTLHandler(Long connectionTtlMillis) {
        if (connectionTtlMillis <= 0) {
            throw new IllegalArgumentException("connectTTL must be positive");
        }
        this.connectionTtlMillis = connectionTtlMillis;
    }

    /**
     * Will schedule a task when the handler added.
     * @param ctx The context to use
     * @throws Exception
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        channelKiller = ctx.channel().eventLoop().schedule(() -> closeChannel(ctx), connectionTtlMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Will cancel the scheduled tasks when handler removed.
     * @param ctx The context to use
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        channelKiller.cancel(false);
    }

    /**
     * Will set RELEASE_CHANNEL as true for the channel attribute when connect-ttl is reached.
     * @param ctx The context to use
     */
    private void closeChannel(ChannelHandlerContext ctx) {
        if (ctx.channel().isOpen()) {
            ctx.channel().attr(RELEASE_CHANNEL).set(true);
        }
    }
}
