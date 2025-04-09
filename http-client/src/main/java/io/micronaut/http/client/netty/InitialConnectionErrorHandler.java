/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.util.AttributeKey;

/**
 * Handler for connection failures that happen during the handshake phases of a connection.
 */
@Internal
@ChannelHandler.Sharable
abstract class InitialConnectionErrorHandler extends ChannelInboundHandlerAdapter {
    private static final AttributeKey<Throwable> FAILURE_KEY =
        AttributeKey.valueOf(InitialConnectionErrorHandler.class, "FAILURE_KEY");

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        setFailureCause(ctx.channel(), cause);
        ctx.close();
    }

    static void setFailureCause(Channel channel, Throwable cause) {
        channel.attr(FAILURE_KEY).set(cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        onNewConnectionFailure(ctx.channel().eventLoop(), ctx.channel().attr(FAILURE_KEY).get());
    }

    protected abstract void onNewConnectionFailure(@NonNull EventLoop eventLoop, @Nullable Throwable cause) throws Exception;
}
