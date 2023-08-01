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
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Writes data from a publisher to a channel.
 *
 * @since 4.1.0
 * @author Jonas Konrad
 */
@Internal
final class ReactiveClientWriter extends ChannelInboundHandlerAdapter implements Subscriber<HttpContent> {
    private final Publisher<HttpContent> source;
    private EventLoop eventLoop;
    private ChannelHandlerContext ctx;
    private Subscription subscription;
    private boolean writtenLast;

    ReactiveClientWriter(Publisher<HttpContent> source) {
        this.source = source;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.eventLoop = ctx.channel().eventLoop();
        this.ctx = ctx;
        source.subscribe(this);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        this.ctx = null;
        if (subscription != null) {
            subscription.cancel();
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        super.channelWritabilityChanged(ctx);
        if (ctx.channel().isWritable()) {
            subscription.request(1);
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> onSubscribe(s));
            return;
        }

        if (ctx == null) {
            s.cancel();
        } else {
            subscription = s;
            if (ctx.channel().isWritable()) {
                subscription.request(1);
            }
        }
    }

    @Override
    public void onNext(HttpContent httpContent) {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> onNext(httpContent));
            return;
        }

        if (writtenLast) {
            throw new IllegalStateException("Already written a LastHttpContent");
        }

        if (ctx == null) {
            httpContent.release();
            return;
        }

        if (httpContent instanceof LastHttpContent) {
            writtenLast = true;
        }
        ctx.writeAndFlush(httpContent, ctx.voidPromise());
        if (ctx.channel().isWritable()) {
            subscription.request(1);
        }
    }

    @Override
    public void onError(Throwable t) {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> onError(t));
            return;
        }

        ctx.fireExceptionCaught(t);
        ctx.pipeline().remove(ctx.name());
    }

    @Override
    public void onComplete() {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(this::onComplete);
            return;
        }

        if (!writtenLast) {
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, ctx.voidPromise());
        }
        ctx.pipeline().remove(ctx.name());
    }
}
