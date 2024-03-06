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
import io.micronaut.http.netty.EventLoopSerializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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
    private EventLoopSerializer serializer;
    private ChannelHandlerContext ctx;
    private Subscription subscription;
    private boolean writtenLast;

    ReactiveClientWriter(Publisher<HttpContent> source) {
        this.source = source;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.serializer = new EventLoopSerializer(ctx.channel().eventLoop());
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
        if (serializer.executeNow(() -> onSubscribe0(s))) {
            onSubscribe0(s);
        }
    }

    private void onSubscribe0(Subscription s) {
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
        if (serializer.executeNow(() -> onNext0(httpContent))) {
            onNext0(httpContent);
        }
    }

    private void onNext0(HttpContent httpContent) {
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
        if (serializer.executeNow(() -> onError0(t))) {
            onError0(t);
        }
    }

    private void onError0(Throwable t) {
        ctx.fireExceptionCaught(t);
        ctx.pipeline().remove(ctx.name());
    }

    @Override
    public void onComplete() {
        if (serializer.executeNow(this::onComplete0)) {
            onComplete0();
        }
    }

    private void onComplete0() {
        if (!writtenLast) {
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, ctx.voidPromise());
        }
        ctx.pipeline().remove(ctx.name());
    }
}
