/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.netty.reactive;

import static io.micronaut.http.netty.reactive.HandlerSubscriber.State.CANCELLED;
import static io.micronaut.http.netty.reactive.HandlerSubscriber.State.COMPLETE;
import static io.micronaut.http.netty.reactive.HandlerSubscriber.State.INACTIVE;
import static io.micronaut.http.netty.reactive.HandlerSubscriber.State.NO_CONTEXT;
import static io.micronaut.http.netty.reactive.HandlerSubscriber.State.NO_SUBSCRIPTION;
import static io.micronaut.http.netty.reactive.HandlerSubscriber.State.NO_SUBSCRIPTION_OR_CONTEXT;
import static io.micronaut.http.netty.reactive.HandlerSubscriber.State.RUNNING;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.EventExecutor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Subscriber that publishes received messages to the handler pipeline.
 *
 * @param <T> The subscriber type
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("SubscriberImplementation")
@Internal
public class HandlerSubscriber<T> extends ChannelDuplexHandler implements Subscriber<T> {

    private final EventExecutor executor;
    private final AtomicBoolean hasSubscription = new AtomicBoolean();

    private volatile Subscription subscription;
    private volatile ChannelHandlerContext ctx;

    private State state = NO_SUBSCRIPTION_OR_CONTEXT;
    private ChannelFuture lastWriteFuture;

    /**
     * Create a new handler subscriber with the default low and high watermarks.
     * <p>
     * The supplied executor must be the same event loop as the event loop that this handler is eventually registered
     * with, if not, an exception will be thrown when the handler is registered.
     *
     * @param executor The executor to execute asynchronous events from the publisher on.
     */
    public HandlerSubscriber(EventExecutor executor) {
        this.executor = executor;
    }

    /**
     * Override for custom error handling. By default, it closes the channel.
     *
     * @param error The error to handle.
     */
    protected void error(Throwable error) {
        doClose();
    }

    /**
     * Override for custom completion handling. By default, it closes the channel.
     */
    protected void complete() {
        doClose();
    }

    /**
     * The state.
     */
    enum State {
        NO_SUBSCRIPTION_OR_CONTEXT,
        NO_SUBSCRIPTION,
        NO_CONTEXT,
        INACTIVE,
        RUNNING,
        CANCELLED,
        COMPLETE
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        verifyRegisteredWithRightExecutor(ctx);

        switch (state) {
            case NO_SUBSCRIPTION_OR_CONTEXT:
                this.ctx = ctx;
                // We were in no subscription or context, now we just don't have a subscription.
                state = NO_SUBSCRIPTION;
                break;
            case NO_CONTEXT:
                this.ctx = ctx;
                // We were in no context, we're now fully initialised
                maybeStart();
                break;
            case COMPLETE:
                // We are complete, close
                state = COMPLETE;
                ctx.close();
                break;
            default:
                throw new IllegalStateException("This handler must only be added to a pipeline once " + state);
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        verifyRegisteredWithRightExecutor(ctx);
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        maybeRequestMore();
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (state == INACTIVE) {
            state = RUNNING;
            maybeRequestMore();
        }
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cancel();
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancel();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cancel();
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void onSubscribe(final Subscription subscription) {
        if (subscription == null) {
            throw new NullPointerException("Null subscription");
        } else if (!hasSubscription.compareAndSet(false, true)) {
            subscription.cancel();
        } else {
            this.subscription = subscription;
            executor.execute(this::provideSubscription);
        }
    }

    @Override
    public void onNext(T t) {
        // Publish straight to the context.
        lastWriteFuture = ctx.writeAndFlush(t);
        lastWriteFuture.addListener((ChannelFutureListener) future -> {
            maybeRequestMore();
        });
    }

    @Override
    public void onError(final Throwable error) {
        if (error == null) {
            throw new NullPointerException("Null error published");
        }
        error(error);
    }

    @Override
    public void onComplete() {
        if (lastWriteFuture == null) {
            complete();
        } else {
            lastWriteFuture.addListener((ChannelFutureListener) channelFuture -> complete());
        }
    }

    private void doClose() {
        executor.execute(() -> {
            switch (state) {
                case NO_SUBSCRIPTION:
                case INACTIVE:
                case RUNNING:
                    ctx.close();
                    state = COMPLETE;
                    break;
                default:
                    // no-op
            }
        });
    }

    private void maybeRequestMore() {
        if (ctx.channel().isWritable() && !(state == COMPLETE || state == CANCELLED)) {
            subscription.request(1);
        }
    }

    private void verifyRegisteredWithRightExecutor(ChannelHandlerContext ctx) {
        if (ctx.channel().isRegistered() && !executor.inEventLoop()) {
            throw new IllegalArgumentException("Channel handler MUST be registered with the same EventExecutor that it is created with.");
        }
    }

    private void cancel() {
        switch (state) {
            case NO_SUBSCRIPTION:
                state = CANCELLED;
                break;
            case RUNNING:
            case INACTIVE:
                subscription.cancel();
                state = CANCELLED;
                break;
            default:
                // no-op
        }
    }

    private void provideSubscription() {
        switch (state) {
            case NO_SUBSCRIPTION_OR_CONTEXT:
                state = NO_CONTEXT;
                break;
            case NO_SUBSCRIPTION:
                maybeStart();
                break;
            case CANCELLED:
                subscription.cancel();
                break;
            default:
                // no-op
        }
    }

    private void maybeStart() {
        if (ctx.channel().isActive()) {
            state = RUNNING;
            maybeRequestMore();
        } else {
            state = INACTIVE;
        }
    }
}
