/*
 * Copyright 2017-2023 original authors
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

/**
 * Resettable version of {@link ReadTimeoutHandler}, as a workaround before
 * <a href="https://github.com/netty/netty/pull/13598">https://github.com/netty/netty/pull/13598</a>
 * is merged. (TODO: move to new API when that is merged)
 *
 * @author Jonas Konrad
 * @since 4.1.4
 */
@Internal
class ResettableReadTimeoutHandler extends ReadTimeoutHandler {
    private static final Object FAKE_MESSAGE = new Object();

    private ChannelHandlerContext ctx;
    private boolean reading = false;

    public ResettableReadTimeoutHandler(long timeout, TimeUnit unit) {
        super(timeout, unit);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        this.ctx = ctx;
        ctx.pipeline().addAfter(ctx.name(), ctx.name() + "-reset-interceptor", NextInterceptor.INSTANCE);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        try {
            ctx.pipeline().remove(ctx.name() + "-reset-interceptor");
        } catch (NoSuchElementException ignored) {
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        reading = true;
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        reading = false;
        super.channelReadComplete(ctx);
    }

    void resetReadTimeoutMn() {
        if (!reading) {
            try {
                channelRead(ctx, FAKE_MESSAGE);
                channelReadComplete(ctx);
            } catch (Exception ignored) {
            }
        }
    }

    @Sharable
    private static class NextInterceptor extends ChannelInboundHandlerAdapter {
        static final NextInterceptor INSTANCE = new NextInterceptor();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg == FAKE_MESSAGE) {
                return;
            }
            super.channelRead(ctx, msg);
        }
    }
}
