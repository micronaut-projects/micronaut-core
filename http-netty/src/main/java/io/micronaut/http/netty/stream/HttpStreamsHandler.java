package io.micronaut.http.netty.stream;

import io.micronaut.http.netty.reactive.HandlerPublisher;
import io.micronaut.http.netty.reactive.HandlerSubscriber;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.LinkedList;
import java.util.Queue;

abstract class HttpStreamsHandler<In extends HttpMessage, Out extends HttpMessage> extends ChannelDuplexHandler {

    private final Queue<Outgoing> outgoing = new LinkedList<>();
    private final Class<In> inClass;
    private final Class<Out> outClass;

    public HttpStreamsHandler(Class<In> inClass, Class<Out> outClass) {
        this.inClass = inClass;
        this.outClass = outClass;
    }

    /**
     * The incoming message that is currently being streamed out to a subscriber.
     *
     * This is tracked so that if its subscriber cancels, we can go into a mode where we ignore the rest of the body.
     * Since subscribers may cancel as many times as they like, including well after they've received all their content,
     * we need to track what the current message that's being streamed out is so that we can ignore it if it's not
     * currently being streamed out.
     */
    private In currentlyStreamedMessage;

    /**
     * Ignore the remaining reads for the incoming message.
     *
     * This is used in conjunction with currentlyStreamedMessage, as well as in situations where we have received the
     * full body, but still might be expecting a last http content message.
     */
    private boolean ignoreBodyRead;

    /**
     * Whether a LastHttpContent message needs to be written once the incoming publisher completes.
     *
     * Since the publisher may itself publish a LastHttpContent message, we need to track this fact, because if it
     * doesn't, then we need to write one ourselves.
     */
    private boolean sendLastHttpContent;

    /**
     * Whether the given incoming message has a body.
     */
    protected abstract boolean hasBody(In in);

    /**
     * Create an empty incoming message. This must be of type FullHttpMessage, and is invoked when we've determined
     * that an incoming message can't have a body, so we send it on as a FullHttpMessage.
     */
    protected abstract In createEmptyMessage(In in);

    /**
     * Create a streamed incoming message with the given stream.
     */
    protected abstract In createStreamedMessage(In in, Publisher<HttpContent> stream);

    /**
     * Invoked when an incoming message is first received.
     *
     * Overridden by sub classes for state tracking.
     */
    protected void receivedInMessage(ChannelHandlerContext ctx) {}

    /**
     * Invoked when an incoming message is fully consumed.
     *
     * Overridden by sub classes for state tracking.
     */
    protected void consumedInMessage(ChannelHandlerContext ctx) {}

    /**
     * Invoked when an outgoing message is first received.
     *
     * Overridden by sub classes for state tracking.
     */
    protected void receivedOutMessage(ChannelHandlerContext ctx) {}

    /**
     * Invoked when an outgoing message is fully sent.
     *
     * Overridden by sub classes for state tracking.
     */
    protected void sentOutMessage(ChannelHandlerContext ctx) {}

    /**
     * Subscribe the given subscriber to the given streamed message.
     *
     * Provided so that the client subclass can intercept this to hold off sending the body of an expect 100 continue
     * request.
     */
    protected void subscribeSubscriberToStream(StreamedHttpMessage msg, Subscriber<HttpContent> subscriber) {
        msg.subscribe(subscriber);
    }

    /**
     * Invoked every time a read of the incoming body is requested by the subscriber.
     *
     * Provided so that the server subclass can intercept this to send a 100 continue response.
     */
    protected void bodyRequested(ChannelHandlerContext ctx) {}

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

        if (inClass.isInstance(msg)) {

            receivedInMessage(ctx);
            final In inMsg = inClass.cast(msg);

            if (inMsg instanceof FullHttpMessage) {

                // Forward as is
                ctx.fireChannelRead(inMsg);
                consumedInMessage(ctx);

            } else if (!hasBody(inMsg)) {

                // Wrap in empty message
                ctx.fireChannelRead(createEmptyMessage(inMsg));
                consumedInMessage(ctx);

                // There will be a LastHttpContent message coming after this, ignore it
                ignoreBodyRead = true;

            } else {

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
            ignoreBodyRead = true;
            // Need to do a read in case the subscriber ignored a read completed.
            ctx.read();
        }
    }

    private void handleReadHttpContent(ChannelHandlerContext ctx, HttpContent content) {
        if (!ignoreBodyRead) {
            if (content instanceof LastHttpContent) {

                if (content.content().readableBytes() > 0 ||
                        !((LastHttpContent) content).trailingHeaders().isEmpty()) {
                    // It has data or trailing headers, send them
                    ctx.fireChannelRead(content);
                } else {
                    ReferenceCountUtil.release(content);
                }

                removeHandlerIfActive(ctx, ctx.name() + "-body-publisher");
                currentlyStreamedMessage = null;
                consumedInMessage(ctx);

            } else {
                ctx.fireChannelRead(content);
            }

        } else {
            ReferenceCountUtil.release(content);
            if (content instanceof LastHttpContent) {
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
        } else {
            ctx.fireChannelReadComplete();
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, final ChannelPromise promise) throws Exception {
        if (outClass.isInstance(msg)) {

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
                    out.promise.tryFailure(error);
                    ctx.close();
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
        } else {
            outgoing.remove().promise.setSuccess();
            sentOutMessage(ctx);
            flushNext(ctx);
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
            ctx.pipeline().remove(name);
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

    class Outgoing {
        final Out message;
        final ChannelPromise promise;

        public Outgoing(Out message, ChannelPromise promise) {
            this.message = message;
            this.promise = promise;
        }
    }
}
