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
package io.micronaut.http.netty.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.reactive.CancelledSubscriber;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Handler that converts written {@link StreamedHttpRequest} messages into {@link HttpRequest} messages
 * followed by {@link HttpContent} messages and reads {@link HttpResponse} messages followed by
 * {@link HttpContent} messages and produces {@link StreamedHttpResponse} messages.
 * <p>
 * This allows request and response bodies to be handled using reactive streams.
 * <p>
 * There are two types of messages that this handler accepts for writing, {@link StreamedHttpRequest} and
 * {@link io.netty.handler.codec.http.FullHttpRequest}. Writing any other messages may potentially lead to HTTP message mangling.
 * <p>
 * There are two types of messages that this handler will send down the chain, {@link StreamedHttpResponse},
 * and {@link FullHttpResponse}. If {@link io.netty.channel.ChannelOption#AUTO_READ} is false for the channel,
 * then any {@link StreamedHttpResponse} messages <em>must</em> be subscribed to consume the body, otherwise
 * it's possible that no read will be done of the messages.
 * <p>
 * As long as messages are returned in the order that they arrive, this handler implicitly supports HTTP
 * pipelining.
 *
 * @author jroper
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class HttpStreamsClientHandler extends HttpStreamsHandler<HttpResponse, HttpRequest> {

    private int inFlight = 0;
    private int withServer = 0;
    private ChannelPromise closeOnZeroInFlight = null;
    private Subscriber<HttpContent> awaiting100Continue;
    private StreamedHttpMessage awaiting100ContinueMessage;
    private boolean ignoreResponseBody = false;

    /**
     * Default constructor.
     */
    public HttpStreamsClientHandler() {
        super(HttpResponse.class, HttpRequest.class);
    }

    @Override
    protected boolean hasBody(HttpResponse response) {
        if (response.status().code() >= HttpStatus.CONTINUE.getCode() && response.status().code() < HttpStatus.OK.getCode()) {
            return false;
        }

        if (response.status().equals(HttpResponseStatus.NO_CONTENT) ||
            response.status().equals(HttpResponseStatus.NOT_MODIFIED)) {
            return false;
        }

        if (HttpUtil.isTransferEncodingChunked(response)) {
            return true;
        }


        if (HttpUtil.isContentLengthSet(response)) {
            return HttpUtil.getContentLength(response) > 0;
        }

        return true;
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise future) throws Exception {
        if (inFlight == 0) {
            ctx.close(future);
        } else {
            closeOnZeroInFlight = future;
        }
    }

    @Override
    protected void consumedInMessage(ChannelHandlerContext ctx) {
        inFlight--;
        withServer--;
        if (inFlight == 0 && closeOnZeroInFlight != null) {
            ctx.close(closeOnZeroInFlight);
        }
    }

    @Override
    protected void receivedOutMessage(ChannelHandlerContext ctx) {
        inFlight++;
    }

    @Override
    protected void sentOutMessage(ChannelHandlerContext ctx) {
        withServer++;
    }

    @Override
    protected HttpResponse createEmptyMessage(HttpResponse response) {
        return new EmptyHttpResponse(response);
    }

    @Override
    protected HttpResponse createStreamedMessage(HttpResponse response, Publisher<HttpContent> stream) {
        return new DelegateStreamedHttpResponse(response, stream);
    }

    @Override
    protected void subscribeSubscriberToStream(StreamedHttpMessage msg, Subscriber<HttpContent> subscriber) {
        if (HttpUtil.is100ContinueExpected(msg)) {
            awaiting100Continue = subscriber;
            awaiting100ContinueMessage = msg;
        } else {
            super.subscribeSubscriberToStream(msg, subscriber);
        }
    }

    @Override
    protected final boolean isClient() {
        return true;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof HttpResponse && awaiting100Continue != null && withServer == 0) {
            HttpResponse response = (HttpResponse) msg;
            if (response.status().equals(HttpResponseStatus.CONTINUE)) {
                super.subscribeSubscriberToStream(awaiting100ContinueMessage, awaiting100Continue);
                awaiting100Continue = null;
                awaiting100ContinueMessage = null;
                if (msg instanceof FullHttpResponse) {
                    ReferenceCountUtil.release(msg);
                } else {
                    ignoreResponseBody = true;
                }
            } else {
                awaiting100ContinueMessage.subscribe(new CancelledSubscriber<>());
                awaiting100ContinueMessage = null;
                awaiting100Continue.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
                awaiting100Continue.onComplete();
                awaiting100Continue = null;
                super.channelRead(ctx, msg);
            }
        } else if (ignoreResponseBody && msg instanceof HttpContent) {

            ReferenceCountUtil.release(msg);
            if (msg instanceof LastHttpContent) {
                ignoreResponseBody = false;
            }
        } else {
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, final ChannelPromise promise) throws Exception {
        if (ctx.channel().attr(AttributeKey.valueOf(ChannelPipelineCustomizer.HANDLER_HTTP_CHUNK)).get() == Boolean.TRUE) {
            ctx.write(msg, promise);
        } else {
            super.write(ctx, msg, promise);
        }
    }
}
