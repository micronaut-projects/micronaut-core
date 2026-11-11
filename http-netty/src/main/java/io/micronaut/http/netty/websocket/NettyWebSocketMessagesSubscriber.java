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
package io.micronaut.http.netty.websocket;

import io.micronaut.http.MediaType;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes the messages of a publisher to the channel of a {@link NettyWebSocketSession}, see
 * {@link NettyWebSocketSession#sendAllAsync}. The next message is requested as soon as the
 * previous one is written to the channel while the channel is writable, and the writes of a
 * batch share one flush. Once the channel is no longer writable, it is flushed and the next
 * message is requested when the last write completed, so at most the high water mark of the
 * channel, and one message, is buffered. All the work runs on the event loop of the channel.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
final class NettyWebSocketMessagesSubscriber implements Subscriber<Object> {

    private final NettyWebSocketSession session;
    private final Channel channel;
    private final EventLoop eventLoop;
    private final MediaType mediaType;
    private final CompletableFuture<Boolean> sent;
    private final ChannelFutureListener writeListener = this::written;
    private final ChannelFutureListener drainedListener = future -> requestMore();
    private final Runnable flushTask = this::flush;
    /**
     * The signals that wait for the event loop: a signal on the event loop runs at once only when
     * none does, so the signals keep their order.
     */
    private final AtomicInteger queued = new AtomicInteger();

    // the fields below are read and written on the event loop only
    @Nullable
    private Subscription subscription;
    private boolean cancelled;
    private boolean terminated;
    private boolean requesting;
    private boolean requestAgain;
    private boolean flushScheduled;
    @Nullable
    private ChannelFuture lastWrite;

    /**
     * @param session   The session
     * @param channel   The channel of the session
     * @param mediaType The media type of the messages
     * @param sent      Completes with {@code true} when all the messages were written, with
     *                  {@code false} when the session closed first: completing it before, e.g.
     *                  cancelling it, cancels the publisher
     */
    NettyWebSocketMessagesSubscriber(NettyWebSocketSession session, Channel channel, MediaType mediaType, CompletableFuture<Boolean> sent) {
        this.session = session;
        this.channel = channel;
        this.eventLoop = channel.eventLoop();
        this.mediaType = mediaType;
        this.sent = sent;
        sent.whenComplete((ignored, error) -> onEventLoop(this::cancelIfNotTerminated));
    }

    @Override
    public void onSubscribe(Subscription s) {
        onEventLoop(() -> {
            if (subscription != null) {
                s.cancel();
                return;
            }
            subscription = s;
            if (sent.isDone()) {
                cancelIfNotTerminated();
            } else {
                requestMore();
            }
        });
    }

    @Override
    public void onNext(Object message) {
        onEventLoop(() -> write(message));
    }

    @Override
    public void onError(Throwable t) {
        onEventLoop(() -> {
            terminated = true;
            flush();
            sent.completeExceptionally(t);
        });
    }

    @Override
    public void onComplete() {
        onEventLoop(() -> {
            terminated = true;
            flush();
            ChannelFuture last = lastWrite;
            if (last == null) {
                sent.complete(true);
            } else {
                // a failed write fails the future, see written
                last.addListener(future -> {
                    if (future.isSuccess()) {
                        sent.complete(true);
                    }
                });
            }
        });
    }

    private void onEventLoop(Runnable signal) {
        if (eventLoop.inEventLoop() && queued.get() == 0) {
            signal.run();
        } else {
            queued.incrementAndGet();
            eventLoop.execute(() -> {
                queued.decrementAndGet();
                signal.run();
            });
        }
    }

    private void write(Object message) {
        if (sent.isDone()) {
            // cancelled: the publisher may still emit what it had
            ReferenceCountUtil.release(message);
            return;
        }
        if (!session.isOpen()) {
            ReferenceCountUtil.release(message);
            sent.complete(false);
            return;
        }
        WebSocketFrame frame;
        try {
            frame = session.encodeMessage(message, mediaType);
        } catch (RuntimeException e) {
            sent.completeExceptionally(e);
            return;
        }
        ChannelFuture write = channel.write(frame);
        write.addListener(writeListener);
        lastWrite = write;
        if (channel.isWritable()) {
            if (!flushScheduled) {
                // one flush for the messages written in this run of the event loop
                flushScheduled = true;
                eventLoop.execute(flushTask);
            }
            requestMore();
        } else {
            // the next message once the written ones are out
            flush();
            write.addListener(drainedListener);
        }
    }

    private void written(ChannelFuture future) {
        if (!future.isSuccess()) {
            Throwable cause = future.cause();
            if (session.isOpen()) {
                sent.completeExceptionally(new WebSocketSessionException("Send Failure: " + cause.getMessage(), cause));
            } else {
                // the session closed: there is no one to send the rest to
                sent.complete(false);
            }
        }
    }

    private void flush() {
        flushScheduled = false;
        channel.flush();
    }

    private void requestMore() {
        if (requesting) {
            // a message written while requesting the previous one: the loop below requests
            requestAgain = true;
            return;
        }
        requesting = true;
        try {
            do {
                requestAgain = false;
                Subscription s = subscription;
                if (s == null || cancelled || terminated || sent.isDone()) {
                    return;
                }
                s.request(1);
            } while (requestAgain);
        } finally {
            requesting = false;
        }
    }

    private void cancelIfNotTerminated() {
        Subscription s = subscription;
        if (s != null && !cancelled && !terminated) {
            cancelled = true;
            s.cancel();
        }
        if (flushScheduled) {
            flush();
        }
    }
}
