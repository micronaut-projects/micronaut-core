/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.reactive.HotObservable;
import io.micronaut.http.server.netty.body.ByteBody;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http2.Http2Exception;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;

/**
 * Common handler implementation for multiplexed HTTP versions (HTTP/2 and HTTP/3).
 *
 * @since 4.4.0
 * @author Jonas Konrad
 */
@Internal
abstract class MultiplexedServerHandler {
    final Logger LOG = LoggerFactory.getLogger(getClass());

    ChannelHandlerContext ctx;
    private final RequestHandler requestHandler;

    MultiplexedServerHandler(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    /**
     * Flush the channel.
     */
    abstract void flush();

    /**
     * An HTTP/2 or HTTP/3 stream.
     */
    abstract class MultiplexedStream implements OutboundAccess {
        private HttpRequest request;

        private List<ByteBuf> bufferedContent;
        private InputStreamer streamer;

        private Object attachment;

        private boolean requestAccepted;
        private boolean responseDone;
        private BlockingWriter blockingWriter;

        /**
         * Called when the controller consumes some HTTP request data.
         *
         * @param n The number of bytes that have been consumed
         */
        abstract void notifyDataConsumed(int n);

        /**
         * Reset this stream.
         *
         * @param cause The exception that caused this stream to reset
         * @return {@code true} if this exception contained an error code and thus need not be
         * logged, {@code false} if it should be logged
         */
        abstract boolean reset(Throwable cause);

        /**
         * Close the input of the stream.
         */
        abstract void closeInput();

        /**
         * Called when the request headers are read.
         *
         * @param headers The headers
         * @param endOfStream Whether this is the last request packet
         */
        final void onHeadersRead(HttpRequest headers, boolean endOfStream) {
            if (requestAccepted) {
                throw new IllegalStateException("Request already accepted");
            }

            this.request = headers;
            if (endOfStream) {
                requestAccepted = true;
                requestHandler.accept(ctx, headers, ByteBody.empty(), this);
            }
        }

        /**
         * Called when a data frame is read.
         *
         * @param data The input data. Release ownership is transferred to this method
         * @param endOfStream Whether this is the last request packet
         * @return The number of bytes that have been consumed immediately (like
         * {@link #notifyDataConsumed(int)})
         */
        final int onDataRead(ByteBuf data, boolean endOfStream) {
            data.retain();
            if (streamer == null) {
                if (requestAccepted) {
                    throw new IllegalStateException("Request already accepted");
                }

                if (endOfStream) {
                    // we got the full message before readComplete
                    ByteBuf fullBody;
                    if (bufferedContent == null) {
                        fullBody = data;
                    } else {
                        CompositeByteBuf composite = ctx.alloc().compositeBuffer();
                        for (ByteBuf c : bufferedContent) {
                            composite.addComponent(true, c);
                        }
                        composite.addComponent(true, data);
                        fullBody = composite;
                    }
                    bufferedContent = null;

                    requestAccepted = true;
                    notifyDataConsumed(fullBody.readableBytes());
                    requestHandler.accept(ctx, request, ByteBody.of(fullBody), this);
                } else {
                    if (bufferedContent == null) {
                        bufferedContent = new ArrayList<>();
                    }
                    bufferedContent.add(data);
                }
            } else {
                DefaultHttpContent c = endOfStream ? new DefaultLastHttpContent(data) : new DefaultHttpContent(data);
                if (streamer.sink.tryEmitNext(c) != Sinks.EmitResult.OK) {
                    c.release();
                }
                if (endOfStream) {
                    streamer.sink.tryEmitComplete();
                }
            }
            return 0;
        }

        /**
         * Called on read complete. This makes the stream devolve into streaming mode, i.e. give up
         * on buffering data in hopes of reading it all in one go.
         */
        final void devolveToStreaming() {
            if (requestAccepted || streamer != null || request == null) {
                return;
            }
            streamer = new InputStreamer();
            if (bufferedContent != null) {
                for (ByteBuf buf : bufferedContent) {
                    streamer.sink.tryEmitNext(new DefaultHttpContent(buf));
                }
                bufferedContent = null;
            }
            requestAccepted = true;
            requestHandler.accept(ctx, request, ByteBody.of(streamer, request.headers().getInt(HttpHeaderNames.CONTENT_LENGTH, -1)), this);
        }

        /**
         * Called on goaway.
         *
         * @param e The exception that should be forwarded to the stream consumer
         */
        final void onGoAwayRead(Exception e) {
            onRstStreamRead(e);
        }

        /**
         * Called on rst.
         *
         * @param e The exception that should be forwarded to the stream consumer
         */
        final void onRstStreamRead(Exception e) {
            if (streamer != null) {
                streamer.sink.tryEmitError(e);
            }
            finish();
        }

        private boolean finish() {
            if (responseDone) {
                return false;
            }
            responseDone = true;
            if (blockingWriter != null) {
                blockingWriter.discard();
            }
            requestHandler.responseWritten(attachment);
            return true;
        }

        @Override
        public final @NonNull ByteBufAllocator alloc() {
            return ctx.alloc();
        }

        @Override
        public final void writeFull(@NonNull FullHttpResponse response, boolean headResponse) {
            if (responseDone) {
                // early check
                throw new IllegalStateException("Response already written");
            }
            if (!ctx.executor().inEventLoop()) {
                ctx.executor().execute(() -> writeFull(response, headResponse));
                return;
            }

            ByteBuf content = response.content();
            boolean empty = !content.isReadable();
            writeHeaders(response, empty, ctx.voidPromise());
            if (!empty) {
                writeData(content, true, ctx.voidPromise());
            }
            if (!finish()) {
                throw new IllegalStateException("Response already written");
            }
            flush();
        }

        @Override
        public final void writeStreamed(@NonNull HttpResponse response, @NonNull Publisher<HttpContent> content) {
            if (responseDone) {
                // early check
                throw new IllegalStateException("Response already written");
            }
            if (!ctx.executor().inEventLoop()) {
                ctx.executor().execute(() -> writeStreamed(response, content));
                return;
            }

            writeHeaders(response, false, ctx.voidPromise());
            content.subscribe(new Subscriber<>() {
                final EventLoopFlow flow = new EventLoopFlow(ctx.channel().eventLoop());
                Subscription subscription;

                @Override
                public void onSubscribe(Subscription s) {
                    subscription = s;
                    s.request(1);
                }

                @Override
                public void onNext(HttpContent httpContent) {
                    if (flow.executeNow(() -> onNext0(httpContent))) {
                        onNext0(httpContent);
                    }
                }

                private void onNext0(HttpContent content) {
                    writeData(content.content(), false, ctx.newPromise()
                            .addListener((ChannelFutureListener) future -> {
                                if (future.isSuccess()) {
                                    subscription.request(1);
                                } else {
                                    logStreamWriteFailure(future.cause());
                                    subscription.cancel();
                                }
                            }));
                    flush();
                }

                @Override
                public void onError(Throwable t) {
                    if (flow.executeNow(() -> onError0(t))) {
                        onError0(t);
                    }
                }

                private void onError0(Throwable t) {
                    EventLoop eventLoop = ctx.channel().eventLoop();
                    if (!eventLoop.inEventLoop()) {
                        eventLoop.execute(() -> onError(t));
                        return;
                    }

                    if (!reset(t)) {
                        LOG.warn("Reactive response received an error after some data has already been written. This error cannot be forwarded to the client.", t);
                    }
                    finish();
                    flush();
                }

                @Override
                public void onComplete() {
                    if (flow.executeNow(this::onComplete0)) {
                        onComplete0();
                    }
                }

                private void onComplete0() {
                    EventLoop eventLoop = ctx.channel().eventLoop();
                    if (!eventLoop.inEventLoop()) {
                        eventLoop.execute(this::onComplete);
                        return;
                    }

                    if (finish()) {
                        writeData(Unpooled.EMPTY_BUFFER, true, ctx.voidPromise());
                        flush();
                    }
                }
            });
        }

        private void logStreamWriteFailure(Throwable cause) {
            if (cause instanceof Http2Exception h2e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Stream shut down by client while sending data", h2e);
                }
            } else {
                LOG.debug("Stream shut down by client while sending data", cause);
            }
        }

        @Override
        public final void writeStream(@NonNull HttpResponse response, @NonNull InputStream stream, @NonNull ExecutorService executorService) {
            if (responseDone) {
                // early check
                throw new IllegalStateException("Response already written");
            }
            if (!ctx.executor().inEventLoop()) {
                ctx.executor().execute(() -> writeStream(response, stream, executorService));
                return;
            }

            blockingWriter = new BlockingWriter(ctx.alloc(), stream, executorService) {
                int lastSuspend = 0;

                @Override
                protected void writeStart() {
                    writeHeaders(response, false, ctx.voidPromise());
                }

                @Override
                protected boolean writeData(ByteBuf buf) {
                    ChannelPromise promise = ctx.newPromise();
                    MultiplexedStream.this.writeData(buf, false, promise);
                    flush();
                    if (promise.isSuccess()) {
                        return true;
                    }
                    int suspend = ++lastSuspend;
                    promise.addListener((ChannelFutureListener) future -> {
                        if (!ctx.executor().inEventLoop()) {
                            throw new IllegalStateException("Should complete in event loop");
                        }
                        if (future.isSuccess()) {
                            // make sure that there's not another queued write that will also call
                            // writeSome when completed
                            if (suspend == lastSuspend) {
                                writeSome();
                            }
                        } else {
                            logStreamWriteFailure(future.cause());
                            discard();
                        }
                    });
                    return false;
                }

                @Override
                protected void writeLast() {
                    MultiplexedStream.this.writeData(Unpooled.EMPTY_BUFFER, true, ctx.voidPromise());
                    flush();
                    finish();
                }

                @Override
                protected void writeSomeAsync() {
                    ctx.executor().execute(this::writeSome);
                }
            };
            blockingWriter.writeSome();
        }

        @Override
        public final void attachment(Object attachment) {
            this.attachment = attachment;
        }

        @Override
        public final void closeAfterWrite() {
        }

        /**
         * Write the response headers.
         *
         * @param headers The response that should be transformed to headers
         * @param endStream Whether this is the last response frame
         * @param promise The promise to complete when the headers are written
         */
        abstract void writeHeaders(HttpResponse headers, boolean endStream, ChannelPromise promise);

        /**
         * Write response data.
         *
         * @param data The data bytes
         * @param endStream Whether this is the last response frame
         * @param promise The promise to complete when the data is written (used for backpressure)
         */
        abstract void writeData(ByteBuf data, boolean endStream, ChannelPromise promise);

        /**
         * This is the {@link HotObservable} that represents the request body in the streaming
         * request case.
         */
        private class InputStreamer implements HotObservable<HttpContent> {
            final Queue<HttpContent> queue = Queues.<HttpContent>unbounded().get();
            final Sinks.Many<HttpContent> sink = Sinks.many().unicast().onBackpressureBuffer(queue);
            final Flux<HttpContent> flux = sink.asFlux()
                .doOnNext(this::onNext);

            private void onNext(HttpContent c) {
                onNext(c.content().readableBytes());
            }

            private void onNext(int n) {
                EventLoop eventLoop = ctx.channel().eventLoop();
                if (!eventLoop.inEventLoop()) {
                    eventLoop.execute(() -> onNext(n));
                    return;
                }

                notifyDataConsumed(n);
                if (queue.isEmpty()) {
                    // flush any window updates
                    flush();
                }
            }

            @Override
            public void closeIfNoSubscriber() {
                EventLoop eventLoop = ctx.channel().eventLoop();
                if (!eventLoop.inEventLoop()) {
                    eventLoop.execute(this::closeIfNoSubscriber);
                    return;
                }

                if (sink.currentSubscriberCount() == 0) {
                    // this prevents further onData calls from leaking items
                    sink.tryEmitError(SubscribedTooLateException.INSTANCE);
                    closeInput();
                }
            }

            @Override
            public void subscribe(Subscriber<? super HttpContent> s) {
                flux.subscribe(s);
            }
        }
    }

    private static final class SubscribedTooLateException extends RuntimeException {
        static final SubscribedTooLateException INSTANCE = new SubscribedTooLateException();

        SubscribedTooLateException() {
            super("Subscribed too late to message body (after closeIfNoSubscriber)");
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
