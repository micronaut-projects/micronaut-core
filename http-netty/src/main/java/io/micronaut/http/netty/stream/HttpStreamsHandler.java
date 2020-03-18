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
package io.micronaut.http.netty.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.reactive.HandlerPublisher;
import io.micronaut.http.netty.reactive.HandlerSubscriber;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Base class for Http Streams handlers.
 *
 * @param <In>  The input Http Message
 * @param <Out> The output Http Message
 *
 * @author jroper
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
abstract class HttpStreamsHandler<In extends HttpMessage, Out extends HttpMessage> extends ChannelDuplexHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HttpStreamsHandler.class);
    private final Queue<Outgoing> outgoing = new LinkedList<>();
    private final Class<In> inClass;
    private final Class<Out> outClass;

    /**
     * The incoming message that is currently being streamed out to a subscriber.
     * <p>
     * This is tracked so that if its subscriber cancels, we can go into a mode where we ignore the rest of the body.
     * Since subscribers may cancel as many times as they like, including well after they've received all their content,
     * we need to track what the current message that's being streamed out is so that we can ignore it if it's not
     * currently being streamed out.
     */
    private In currentlyStreamedMessage;

    /**
     * Ignore the remaining reads for the incoming message.
     * <p>
     * This is used in conjunction with currentlyStreamedMessage, as well as in situations where we have received the
     * full body, but still might be expecting a last http content message.
     */
    private volatile boolean ignoreBodyRead;

    /**
     * Whether a LastHttpContent message needs to be written once the incoming publisher completes.
     * <p>
     * Since the publisher may itself publish a LastHttpContent message, we need to track this fact, because if it
     * doesn't, then we need to write one ourselves.
     */
    private boolean sendLastHttpContent;

    /**
     * @param inClass  The in class
     * @param outClass The out class
     */
    HttpStreamsHandler(Class<In> inClass, Class<Out> outClass) {
        this.inClass = inClass;
        this.outClass = outClass;
    }

    /**
     * Whether the given incoming message has a body.
     *
     * @param in The incoming message
     * @return Whether the incoming message has body
     */
    protected abstract boolean hasBody(In in);

    /**
     * Create an empty incoming message. This must be of type FullHttpMessage, and is invoked when we've determined
     * that an incoming message can't have a body, so we send it on as a FullHttpMessage.
     *
     * @param in The incoming message
     * @return An empty incoming message
     */
    protected abstract In createEmptyMessage(In in);

    /**
     * Create a streamed incoming message with the given stream.
     *
     * @param in     The incoming message
     * @param stream The publisher for the Http Content
     * @return An streamed incoming message
     */
    protected abstract In createStreamedMessage(In in, Publisher<HttpContent> stream);

    /**
     * Invoked when an incoming message is first received.
     * <p>
     * Overridden by sub classes for state tracking.
     *
     * @param ctx The channel handler context
     */
    protected void receivedInMessage(ChannelHandlerContext ctx) {
    }

    /**
     * Invoked when an incoming message is fully consumed.
     * <p>
     * Overridden by sub classes for state tracking.
     *
     * @param ctx The channel handler context
     */
    protected void consumedInMessage(ChannelHandlerContext ctx) {
    }

    /**
     * Invoked when an outgoing message is first received.
     * <p>
     * Overridden by sub classes for state tracking.
     *
     * @param ctx The channel handler context
     */
    protected void receivedOutMessage(ChannelHandlerContext ctx) {
    }

    /**
     * Invoked when an outgoing message is fully sent.
     * <p>
     * Overridden by sub classes for state tracking.
     *
     * @param ctx The channel handler context
     */
    protected void sentOutMessage(ChannelHandlerContext ctx) {
    }

    /**
     * Subscribe the given subscriber to the given streamed message.
     * <p>
     * Provided so that the client subclass can intercept this to hold off sending the body of an expect 100 continue
     * request.
     *
     * @param msg        The streamed Http message
     * @param subscriber The subscriber for the Http Content
     */
    protected void subscribeSubscriberToStream(StreamedHttpMessage msg, Subscriber<HttpContent> subscriber) {
        msg.subscribe(subscriber);
    }

    /**
     * Invoked every time a read of the incoming body is requested by the subscriber.
     * <p>
     * Provided so that the server subclass can intercept this to send a 100 continue response.
     *
     * @param ctx The channel handler context
     */
    protected void bodyRequested(ChannelHandlerContext ctx) {
    }

    /**
     * @return Whether this is the client stream handler.
     */
    protected abstract boolean isClient();

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Reading message");
        }
        if (isValidInMessage(msg)) {

            receivedInMessage(ctx);
            final In inMsg = inClass.cast(msg);

            if (inMsg instanceof FullHttpMessage) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("full message");
                }
                // Forward as is
                ctx.fireChannelRead(inMsg);
                consumedInMessage(ctx);

            } else if (!hasBody(inMsg)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("empty message");
                }
                // Wrap in empty message
                ctx.fireChannelRead(createEmptyMessage(inMsg));
                consumedInMessage(ctx);

                // There will be a LastHttpContent message coming after this, ignore it
                LOG.trace("setting ignore body read to true (empty). instance = {}", System.identityHashCode(this));
                ignoreBodyRead = true;

            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("streaming");
                }
                currentlyStreamedMessage = inMsg;
                // It has a body, stream it
                HandlerPublisher<HttpContent> publisher = new HandlerPublisher<HttpContent>(ctx.executor(), HttpContent.class) {
                    @Override
                    protected void cancelled() {
                        if (ctx.executor().inEventLoop()) {
                            handleCancelled(ctx, inMsg);
                        } else {
                            ctx.executor().execute(() -> handleCancelled(ctx, inMsg));
                        }
                    }

                    @Override
                    protected void requestDemand() {
                        bodyRequested(ctx);
                        super.requestDemand();
                    }
                };

                ctx.channel().pipeline().addAfter(ctx.name(), ctx.name() + "-body-publisher", publisher);
                ctx.fireChannelRead(createStreamedMessage(inMsg, publisher));
            }
        } else if (msg instanceof HttpContent) {
            handleReadHttpContent(ctx, (HttpContent) msg);
        }
    }

    private void handleCancelled(ChannelHandlerContext ctx, In msg) {
        if (currentlyStreamedMessage == msg) {
            LOG.trace("setting ignore body read to true (cancelled). instance = {}", System.identityHashCode(this));
            ignoreBodyRead = true;
            // Need to do a read in case the subscriber ignored a read completed.
            if (LOG.isTraceEnabled()) {
                LOG.trace("Calling ctx.read() for cancelled subscription");
            }
            if (isClient()) {
                ctx.read();
            } else {
                ctx.fireChannelWritabilityChanged();
            }
        }
    }

    private void handleReadHttpContent(ChannelHandlerContext ctx, HttpContent content) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("content is last content = {}", content instanceof LastHttpContent);
            LOG.trace("ignore body read = {}", ignoreBodyRead);
        }
        if (!ignoreBodyRead) {
            ctx.fireChannelRead(content);

            if (content instanceof LastHttpContent) {
                removeHandlerIfActive(ctx, ctx.name() + "-body-publisher");
                currentlyStreamedMessage = null;
                consumedInMessage(ctx);
            }
        } else {
            ReferenceCountUtil.release(content, content.refCnt());
            if (content instanceof LastHttpContent) {
                LOG.trace("setting ignore body read to false (reset). instance = {}", System.identityHashCode(this));
                ignoreBodyRead = false;
                if (currentlyStreamedMessage != null) {
                    removeHandlerIfActive(ctx, ctx.name() + "-body-publisher");
                }
                currentlyStreamedMessage = null;
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (ignoreBodyRead) {
            ctx.read();
            ignoreBodyRead = false;
        } else {
            ctx.fireChannelReadComplete();
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, final ChannelPromise promise) throws Exception {
        if (isValidOutMessage(msg)) {

            Outgoing out = new Outgoing(outClass.cast(msg), promise);
            receivedOutMessage(ctx);

            if (outgoing.isEmpty()) {
                outgoing.add(out);
                flushNext(ctx);
            } else {
                outgoing.add(out);
            }

        } else if (msg instanceof LastHttpContent) {

            sendLastHttpContent = false;
            ctx.write(msg, promise);
        } else {

            ctx.write(msg, promise);
        }
    }

    /**
     * @param ctx The channel handler context
     * @param out The output stream
     */
    protected void unbufferedWrite(final ChannelHandlerContext ctx, final Outgoing out) {

        if (out.message instanceof FullHttpMessage) {
            // Forward as is
            ctx.writeAndFlush(out.message, out.promise);
            out.promise.addListener((ChannelFutureListener) channelFuture -> executeInEventLoop(ctx, () -> {
                sentOutMessage(ctx);
                outgoing.remove();
                flushNext(ctx);
            }));

        } else if (out.message instanceof StreamedHttpMessage) {

            StreamedHttpMessage streamed = (StreamedHttpMessage) out.message;
            HandlerSubscriber<HttpContent> subscriber = new HandlerSubscriber<HttpContent>(ctx.executor()) {
                @Override
                protected void error(Throwable error) {
                    try {

                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error occurred writing stream response: " + error.getMessage(), error);
                        }
                        ctx.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR))
                           .addListener(ChannelFutureListener.CLOSE);
                    } finally {
                        ctx.read();
                    }
                }

                @Override
                protected void complete() {
                    executeInEventLoop(ctx, () -> completeBody(ctx));
                }
            };

            sendLastHttpContent = true;

            // DON'T pass the promise through, create a new promise instead.
            ctx.writeAndFlush(out.message);

            ctx.pipeline().addAfter(ctx.name(), ctx.name() + "-body-subscriber", subscriber);
            subscribeSubscriberToStream(streamed, subscriber);
        }

    }

    private void completeBody(final ChannelHandlerContext ctx) {
        removeHandlerIfActive(ctx, ctx.name() + "-body-subscriber");

        if (sendLastHttpContent) {
            ChannelPromise promise = outgoing.peek().promise;
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, promise).addListener(
                (ChannelFutureListener) channelFuture -> executeInEventLoop(ctx, () -> {
                    outgoing.remove();
                    sentOutMessage(ctx);
                    flushNext(ctx);
                })
            );
            ctx.read();
        } else {
            outgoing.remove().promise.setSuccess();
            sentOutMessage(ctx);
            flushNext(ctx);
            ctx.read();
        }
    }

    /**
     * Most operations we want to do even if the channel is not active, because if it's not, then we want to encounter
     * the error that occurs when that operation happens and so that it can be passed up to the user. However, removing
     * handlers should only be done if the channel is active, because the error that is encountered when they aren't
     * makes no sense to the user (NoSuchElementException).
     */
    private void removeHandlerIfActive(ChannelHandlerContext ctx, String name) {
        if (ctx.channel().isActive()) {
            ChannelPipeline pipeline = ctx.pipeline();
            ChannelHandler handler = pipeline.get(name);
            if (handler != null) {
                pipeline.remove(name);
            }
        }
    }

    private void flushNext(ChannelHandlerContext ctx) {
        if (!outgoing.isEmpty()) {
            unbufferedWrite(ctx, outgoing.element());
        } else {
            ctx.fireChannelWritabilityChanged();
        }
    }

    private void executeInEventLoop(ChannelHandlerContext ctx, Runnable runnable) {
        if (ctx.executor().inEventLoop()) {
            runnable.run();
        } else {
            ctx.executor().execute(runnable);
        }
    }

    /**
     * @param msg The message
     * @return True if the handler should write the message
     */
    protected boolean isValidOutMessage(Object msg) {
        return outClass.isInstance(msg);
    }

    /**
     * @param msg The message
     * @return True if the handler should read the message
     */
    protected boolean isValidInMessage(Object msg) {
        return inClass.isInstance(msg);
    }

    /**
     * The outgoing class.
     */
    class Outgoing {
        final Out message;
        final ChannelPromise promise;

        /**
         * @param message The output message
         * @param promise The channel promise
         */
        Outgoing(Out message, ChannelPromise promise) {
            this.message = message;
            this.promise = promise;
        }
    }

}
