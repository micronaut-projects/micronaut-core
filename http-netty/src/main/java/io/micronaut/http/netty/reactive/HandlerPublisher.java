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

import static io.micronaut.http.netty.reactive.HandlerPublisher.State.*;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.TypeParameterMatcher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publisher for a Netty Handler.
 * <p>
 * This publisher supports only one subscriber.
 * <p>
 * All interactions with the subscriber are done from the handlers executor, hence, they provide the same happens before
 * semantics that Netty provides.
 * <p>
 * The handler publishes all messages that match the type as specified by the passed in class. Any non matching messages
 * are forwarded to the next handler.
 * <p>
 * The publisher will signal complete if it receives a channel inactive event.
 * <p>
 * The publisher will release any messages that it drops (for example, messages that are buffered when the subscriber
 * cancels), but other than that, it does not release any messages.  It is up to the subscriber to release messages.
 * <p>
 * If the subscriber cancels, the publisher will send a close event up the channel pipeline.
 * <p>
 * All errors will short circuit the buffer, and cause publisher to immediately call the subscribers onError method,
 * dropping the buffer.
 * <p>
 * The publisher can be subscribed to or placed in a handler chain in any order.
 *
 * @param <T> The publisher type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class HandlerPublisher<T> extends ChannelDuplexHandler implements HotObservable<T> {
    private static final Logger LOG = LoggerFactory.getLogger(HandlerPublisher.class);
    /**
     * Used for buffering a completion signal.
     */
    private static final Object COMPLETE = new Object() {
        @Override
        public String toString() {
            return "COMPLETE";
        }
    };
    private final AtomicBoolean completed = new AtomicBoolean(false);

    private final EventExecutor executor;
    private final TypeParameterMatcher matcher;

    private final Queue<Object> buffer = new LinkedList<>();

    /**
     * Whether a subscriber has been provided. This is used to detect whether two subscribers are subscribing
     * simultaneously.
     */
    private final AtomicBoolean hasSubscriber = new AtomicBoolean();

    private State state = NO_SUBSCRIBER_OR_CONTEXT;

    private volatile Subscriber<? super T> subscriber;
    private ChannelHandlerContext ctx;
    private long outstandingDemand = 0;
    private Throwable noSubscriberError;

    /**
     * Create a handler publisher.
     * <p>
     * The supplied executor must be the same event loop as the event loop that this handler is eventually registered
     * with, if not, an exception will be thrown when the handler is registered.
     *
     * @param executor              The executor to execute asynchronous events from the subscriber on.
     * @param subscriberMessageType The type of message this publisher accepts.
     */
    public HandlerPublisher(EventExecutor executor, Class<? extends T> subscriberMessageType) {
        this.executor = executor;
        this.matcher = TypeParameterMatcher.get(subscriberMessageType);
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        if (subscriber == null) {
            throw new NullPointerException("Null subscriber");
        }

        if (!hasSubscriber.compareAndSet(false, true)) {
            // Must call onSubscribe first.
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
            subscriber.onError(new IllegalStateException("This publisher only supports one subscriber"));
        } else {
            executor.execute(() -> provideSubscriber(subscriber));
        }
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link io.netty.channel.ChannelInboundHandler} in the {@link io.netty.channel.ChannelPipeline}.
     *
     * @param msg The message to check.
     * @return True if the message should be accepted.
     */
    protected boolean acceptInboundMessage(Object msg) {
        return matcher.match(msg);
    }

    /**
     * Override to handle when a subscriber cancels the subscription.
     * <p>
     * By default, this method will simply close the channel.
     */
    protected void cancelled() {
        ctx.close();
    }

    /**
     * Override to intercept when demand is requested.
     * <p>
     * By default, a channel read is invoked.
     */
    protected void requestDemand() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Demand received for next message (state = " + state + "). Calling context.read()");
        }

        ctx.read();
    }

    /**
     * The state.
     */
    enum State {
        /**
         * Initial state. There's no subscriber, and no context.
         */
        NO_SUBSCRIBER_OR_CONTEXT,

        /**
         * A subscriber has been provided, but no context has been provided.
         */
        NO_CONTEXT,

        /**
         * A context has been provided, but no subscriber has been provided.
         */
        NO_SUBSCRIBER,

        /**
         * An error has been received, but there's no subscriber to receive it.
         */
        NO_SUBSCRIBER_ERROR,

        /**
         * There is no demand, and we have nothing buffered.
         */
        IDLE,

        /**
         * There is no demand, and we're buffering elements.
         */
        BUFFERING,

        /**
         * We have nothing buffered, but there is demand.
         */
        DEMANDING,

        /**
         * The stream is complete, however there are still elements buffered for which no demand has come from the subscriber.
         */
        DRAINING,

        /**
         * We're done, in the terminal state.
         */
        DONE
    }

    private void provideSubscriber(Subscriber<? super T> subscriber) {
        this.subscriber = subscriber;
        switch (state) {
            case NO_SUBSCRIBER_OR_CONTEXT:
                state = NO_CONTEXT;
                break;
            case NO_SUBSCRIBER:
                if (buffer.isEmpty()) {
                    state = IDLE;
                } else {
                    state = BUFFERING;
                }
                subscriber.onSubscribe(new ChannelSubscription());
                break;
            case DRAINING:
                subscriber.onSubscribe(new ChannelSubscription());
                break;
            case NO_SUBSCRIBER_ERROR:
                cleanup();
                state = DONE;
                subscriber.onSubscribe(new ChannelSubscription());
                subscriber.onError(noSubscriberError);
                break;
            default:
                // no-op
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        // If the channel is not yet registered, then it's not safe to invoke any methods on it, eg read() or close()
        // So don't provide the context until it is registered.
        if (ctx.channel().isRegistered()) {
            provideChannelContext(ctx);
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        provideChannelContext(ctx);
        ctx.fireChannelRegistered();
    }

    private void provideChannelContext(ChannelHandlerContext ctx) {
        switch (state) {
            case NO_SUBSCRIBER_OR_CONTEXT:
                verifyRegisteredWithRightExecutor(ctx);
                this.ctx = ctx;
                // It's set, we don't have a subscriber
                state = NO_SUBSCRIBER;
                break;
            case NO_CONTEXT:
                verifyRegisteredWithRightExecutor(ctx);
                this.ctx = ctx;
                state = IDLE;
                subscriber.onSubscribe(new ChannelSubscription());
                break;
            default:
                // Ignore, this could be invoked twice by both handlerAdded and channelRegistered.
        }
    }

    private void verifyRegisteredWithRightExecutor(ChannelHandlerContext ctx) {
        if (!executor.inEventLoop()) {
            throw new IllegalArgumentException("Channel handler MUST be registered with the same EventExecutor that it is created with.");
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // If we subscribed before the channel was active, then our read would have been ignored.
        if (state == DEMANDING) {
            requestDemand();
        }
        ctx.fireChannelActive();
    }

    private void receivedDemand(long demand) {
        switch (state) {
            case BUFFERING:
            case DRAINING:
                if (LOG.isTraceEnabled()) {
                    LOG.trace("HandlerPublisher (state: {}) received demand: {}", state, demand);
                }

                if (addDemand(demand)) {
                    flushBuffer();
                }
                break;

            case DEMANDING:
                if (LOG.isTraceEnabled()) {
                    LOG.trace("HandlerPublisher (state: {}) received demand: {}", state, demand);
                }

                if (addDemand(demand)) {
                    flushBuffer();
                }
                break;

            case IDLE:
                if (LOG.isTraceEnabled()) {
                    LOG.trace("HandlerPublisher (state: {}) received demand: {}", state, demand);
                }

                if (addDemand(demand)) {
                    // Important to change state to demanding before doing a read, in case we get a synchronous
                    // read back.
                    state = DEMANDING;
                    requestDemand();
                }
                break;
            default:
                // no-op
        }
    }

    private boolean addDemand(long demand) {

        if (demand <= 0) {
            illegalDemand();
            return false;
        } else {
            if (outstandingDemand < Long.MAX_VALUE) {
                outstandingDemand += demand;
                if (outstandingDemand < 0) {
                    outstandingDemand = Long.MAX_VALUE;
                }
            }
            return true;
        }
    }

    private void illegalDemand() {
        cleanup();
        subscriber.onError(new IllegalArgumentException("Request for 0 or negative elements in violation of Section 3.9 of the Reactive Streams specification"));
        ctx.close();
        state = DONE;
    }

    private void flushBuffer() {
        while (!buffer.isEmpty() && (outstandingDemand > 0 || outstandingDemand == Long.MAX_VALUE)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("HandlerPublisher (state: {}) release message from buffer to satisfy demand: {}", state, outstandingDemand);
            }
            publishMessage(buffer.remove());
        }
        if (buffer.isEmpty()) {
            if (outstandingDemand > 0) {
                if (state == BUFFERING) {
                    state = DEMANDING;
                } // otherwise we're draining
                requestDemand();
            } else if (state == BUFFERING) {
                state = IDLE;
            }
        }
    }

    private void receivedCancel() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("HandlerPublisher (state: {}) received cancellation request", state);
        }

        switch (state) {
            case BUFFERING:
            case DEMANDING:
            case IDLE:
                cancelled();
            case DRAINING:
                state = DONE;
                break;
            default:
                // no-op
        }
        cleanup();
        subscriber = null;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        if (acceptInboundMessage(message)) {
            switch (state) {
                case IDLE:
                    if (LOG.isTraceEnabled()) {
                        Object msg = messageForTrace(message);
                        LOG.trace("HandlerPublisher (state: IDLE) buffering message: {}", msg);
                    }
                    buffer.add(message);
                    state = BUFFERING;
                    break;
                case NO_SUBSCRIBER:
                case BUFFERING:
                    if (LOG.isTraceEnabled()) {
                        Object msg = messageForTrace(message);
                        LOG.trace("HandlerPublisher (state: BUFFERING) buffering message: {}", msg);
                    }
                    buffer.add(message);
                    break;
                case DEMANDING:
                    publishMessage(message);
                    break;
                case DRAINING:
                case DONE:
                    if (LOG.isTraceEnabled()) {
                        Object msg = messageForTrace(message);
                        LOG.trace("HandlerPublisher (state: DONE) releasing message: {}", msg);
                    }
                    ReferenceCountUtil.release(message);
                    break;
                case NO_CONTEXT:
                case NO_SUBSCRIBER_OR_CONTEXT:
                    throw new IllegalStateException("Message received before added to the channel context");
                default:
                    // no-op
            }
        } else {
            ctx.fireChannelRead(message);
        }
    }

    private Object messageForTrace(Object message) {
        Object msg = message;
        if (message instanceof HttpContent) {
            HttpContent content = (HttpContent) message;
            msg = content.content().toString(StandardCharsets.UTF_8);
        }
        return msg;
    }

    private void publishMessage(Object message) {
        if (COMPLETE.equals(message)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("HandlerPublisher (state: {}) complete. Calling onComplete()", state);
            }
            subscriber.onComplete();
            state = DONE;
        } else {
            @SuppressWarnings("unchecked")
            T next = (T) message;
            if (LOG.isTraceEnabled()) {
                LOG.trace("HandlerPublisher (state: {}) emitting next message: {}", state, messageForTrace(next));
            }

            subscriber.onNext(next);
            if (outstandingDemand < Long.MAX_VALUE) {
                outstandingDemand--;
                if (outstandingDemand == 0 && state != DRAINING) {
                    if (buffer.isEmpty()) {
                        state = IDLE;
                    } else {
                        state = BUFFERING;
                    }
                } else if (outstandingDemand > 0 && (state == DEMANDING || state == BUFFERING || state == DRAINING)) {
                    requestDemand();
                }
            } else {
                requestDemand();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        complete();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        complete();
    }

    private void complete() {
        if (completed.compareAndSet(false, true)) {
            switch (state) {
                case NO_SUBSCRIBER:
                case BUFFERING:
                    buffer.add(COMPLETE);
                    state = DRAINING;
                    break;
                case DEMANDING:
                case IDLE:
                    subscriber.onComplete();
                    state = DONE;
                    break;
                case NO_SUBSCRIBER_ERROR:
                    // Ignore, we're already going to complete the stream with an error
                    // when the subscriber subscribes.
                    break;
                default:
                    // no-op
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        switch (state) {
            case NO_SUBSCRIBER:
                noSubscriberError = cause;
                state = NO_SUBSCRIBER_ERROR;
                cleanup();
                break;
            case BUFFERING:
            case DEMANDING:
            case IDLE:
            case DRAINING:
                state = DONE;
                cleanup();
                subscriber.onError(cause);
                break;
            default:
                // no-op
        }
    }

    @Override
    public void closeIfNoSubscriber() {
        if (subscriber == null) {
            state = DONE;
            cleanup();
        }
    }

    /**
     * Release all elements from the buffer.
     */
    private void cleanup() {
        while (!buffer.isEmpty()) {
            ReferenceCountUtil.release(buffer.remove());
        }
    }

    /**
     * A channel subscrition.
     */
    private class ChannelSubscription implements Subscription {

        @Override
        public void request(final long demand) {
            executor.execute(() -> receivedDemand(demand));
        }

        @Override
        public void cancel() {
            executor.execute(HandlerPublisher.this::receivedCancel);
        }
    }
}
