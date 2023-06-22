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
import io.micronaut.http.netty.reactive.HotObservable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Provides incoming {@link HttpContent} as a {@link Publisher}. Note: This handler <b>requires</b>
 * a {@link io.netty.handler.flow.FlowControlHandler}.
 *
 * @since 4.1.0
 * @author Jonas Konrad
 */
@Internal
abstract class ReactiveClientReader extends ChannelInboundHandlerAdapter implements HotObservable<HttpContent>, Subscription {
    private EventLoop eventLoop;
    private ChannelHandlerContext ctx;
    private Subscriber<? super HttpContent> subscriber;
    private long demand;
    private boolean cancelled = false;

    @Override
    public final void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        eventLoop = ctx.channel().eventLoop();
    }

    @Override
    public final void subscribe(Subscriber<? super HttpContent> s) {
        if (subscriber != null) {
            throw new IllegalStateException("Already subscribed");
        }
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> subscribe(s));
            return;
        }

        subscriber = s;
        s.onSubscribe(this);
    }

    @Override
    public final void request(long n) {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(() -> request(n));
            return;
        }

        long oldDemand = demand;
        long newDemand = oldDemand + n;
        if (newDemand < 0) {
            newDemand = Long.MAX_VALUE;
        }
        demand = newDemand;
        // this read call can lead to a channelRead and thus an onNext. If we are already in an
        // onNext, we need to make sure this doesn't happen (onNext must not be nested). For this
        // reason, in channelRead, the demand is decremented *after* the onNext call, so that
        // if we are already in onNext, oldDemand is never 0 here.
        if (oldDemand == 0) {
            ctx.read();
        }
    }

    @Override
    public final void cancel() {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(this::cancel);
            return;
        }
        cancelled = true;
        if (demand == 0) {
            // eat remaining content
            ctx.read();
        }
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean last = msg instanceof LastHttpContent;
        if (cancelled) {
            ((HttpContent) msg).release();
            if (last) {
                remove(ctx);
            } else {
                ctx.read();
            }
        } else {
            assert demand > 0 : "should be ensured by FlowControlHandler";
            subscriber.onNext((HttpContent) msg);
            if (last) {
                cancelled = true;
                remove(ctx);
                subscriber.onComplete();
            } else if (--demand > 0) {
                ctx.read();
            }
        }
    }

    @Override
    public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cancelled) {
            ctx.fireExceptionCaught(cause);
        } else {
            cancelled = true;
            remove(ctx);
            subscriber.onError(cause);
        }
    }

    @Override
    public final void closeIfNoSubscriber() {
        cancel();
    }

    /**
     * Remove this handler.
     *
     * @param ctx The context of this handler
     */
    protected abstract void remove(ChannelHandlerContext ctx);
}
