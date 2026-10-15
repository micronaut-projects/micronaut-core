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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.client.loadbalance.LoadBalancerSelection;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.body.RawDuplexHandler;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.util.HttpHeadersUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.timeout.IdleStateHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One attempt of an exchange on a connection: it sends the request, holds its body back for
 * {@code 100 Continue}, accepts the response or a protocol switch, and reports the outcome to the
 * load balancer once.
 * <p>
 * The upload of the request and the response are two separate states, because the response may
 * arrive before the upload ended, e.g. a streamed body still being written, or a body held back
 * for {@code 100 Continue} that the response rejects. Each state is an immutable value that
 * carries what it owns, and is replaced on every transition, so that what was handed on or
 * released is no longer reachable. {@link #phase()} combines them into the phase of the exchange.
 * <p>
 * The exchange owns the resources of the attempt until it hands each one on, and releases what it
 * still owns once, when it finishes:
 * <ul>
 *     <li>the pool handle: released by {@link #finish}, or handed to the duplex handler of a
 *     switched connection;</li>
 *     <li>the request body: owned by {@link #start} until the listener took over, then held by
 *     {@link Upload.Holding} until it is sent or dropped, written to the connection, or written
 *     by the stream writer of {@link Upload.Streaming} until it is cancelled;</li>
 *     <li>the replay body: closed once a response arrives, or handed to the attempt that sends
 *     the request again;</li>
 *     <li>the load balancer selection: reported or released once, or handed to the attempt that
 *     sends the request again;</li>
 *     <li>the continue fallback timer: cancelled when {@link Upload.Holding} ends.</li>
 * </ul>
 * Every method runs on the event loop of the connection, so the state is not synchronized. The
 * one boundary to other threads is the cancellation of the response flow, which
 * {@link #start} moves to the event loop, see {@link #cancel()}. A state transition is committed
 * before calling out to code that may re-enter the exchange (customizers, the subscriber of the
 * response flow, the pool), and the state is read again afterwards.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ClientExchange implements Http1ResponseHandler.ResponseListener {

    private final NettyHttpClient client;
    private final Logger log;
    private final ConnectionManager.PoolHandle poolHandle;
    private final ExchangePlan plan;
    private final DelayedExecutionFlow<NettyClientByteBodyResponse> sink;

    /**
     * The upload of the request body, and what it owns.
     */
    private Upload upload = Upload.Idle.INSTANCE;
    /**
     * The response, from before the listener took over until the exchange finished.
     */
    private Response response = Response.Preparing.INSTANCE;
    /**
     * Whether the outcome was reported to the load balancer, or the selection released or handed
     * to the next attempt.
     */
    private boolean reported;
    /**
     * Set while the request can still be sent again if the connection fails, i.e. it is eligible
     * and no response was received yet. Owns the copy of the body.
     */
    @Nullable
    private Replay replay;
    /**
     * Set when the connection failed before the response and the request is sent again once the
     * dead connection is released, in {@link #finish}.
     */
    @Nullable
    private Replay pendingRetry;

    ClientExchange(NettyHttpClient client, ConnectionManager.PoolHandle poolHandle, ExchangePlan plan, DelayedExecutionFlow<NettyClientByteBodyResponse> sink) {
        this.client = client;
        this.log = client.getLog();
        this.poolHandle = poolHandle;
        this.plan = plan;
        this.sink = sink;
    }

    /**
     * @return The phase of the exchange, from the upload and response states
     */
    Phase phase() {
        return switch (response) {
            case Response.Preparing preparing -> Phase.PREPARING;
            case Response.Finished finished -> Phase.FINISHED;
            case Response.Upgraded upgraded -> Phase.UPGRADING;
            case Response.Receiving receiving -> Phase.RECEIVING;
            case Response.Awaiting awaiting -> upload instanceof Upload.Holding ? Phase.WAITING_FOR_CONTINUE : Phase.SENDING;
        };
    }

    /**
     * Start the exchange: start the response listener on the connection, finalize the request
     * headers and write the request, or the head only if the body waits for {@code 100 Continue}.
     * If the connection cannot take the request, everything is released and the exchange fails.
     *
     * @param byteBody The request body, owned by the exchange from here on
     */
    void start(CloseableByteBody byteBody) {
        io.micronaut.http.HttpRequest<?> request = plan.request();
        if (log.isDebugEnabled()) {
            log.debug("Sending HTTP {} to {}", request.getMethodName(), request.getUri());
        }
        if (plan.requestedUpgrade() != null && poolHandle.http2) {
            // a protocol switch takes the whole connection, which an HTTP/2 stream is not
            response = new Response.Finished(false);
            upload = Upload.Stopped.INSTANCE;
            byteBody.close();
            poolHandle.release();
            client.completeExceptionallySafe(sink, client.decorate(new HttpClientException("The request asks to upgrade the connection to '" + plan.requestedUpgrade() +
                "', which needs an HTTP/1.1 connection, but the client connects to " + request.getUri().getHost() + " with HTTP/2")));
            return;
        }
        poolHandle.channel.attr(ResponseContentDecompressor.SKIP_DECOMPRESSION)
            .set(plan.skipDecompression() ? Boolean.TRUE : null);

        // owned here until the listener took over
        OutgoingBody body = null;
        try {
            if (byteBody instanceof AvailableByteBody available) {
                if (plan.retryEligible()) {
                    // copy of the request body, kept so that the request can be sent again if the
                    // reused connection turns out to be closed already. An empty body needs no copy
                    replay = new Replay(available.length() != 0 ? available.split() : null);
                }
                body = new OutgoingBody.Buffered(NettyByteBodyFactory.toByteBuf(available));
            } else {
                body = new OutgoingBody.Streamed(new StreamWriter(poolHandle.channel(), new NettyByteBodyFactory(poolHandle.channel()).toStreaming(byteBody), e -> {
                    poolHandle.taint();
                    client.completeExceptionallySafe(sink, e);
                }, plan.uploadListener() == null ? null : plan.uploadListener().uploaded()));
            }
            if (!startListener(body)) {
                // the connection could not take the request, startListener cleaned up
                return;
            }
            if (plan.readIdleTimeout() != null) {
                RequestReadIdleTimeoutHandler.install(poolHandle.http2, poolHandle.channel.pipeline(), plan.readIdleTimeout());
            }
        } catch (Throwable t) {
            abortStart(t, byteBody, body);
            return;
        }
        writeRequest(body);
    }

    /**
     * The request was not written because preparing it failed: don't reuse the connection, and
     * make sure the pool handle is released and the caller sees the error.
     */
    private void abortStart(Throwable t, CloseableByteBody byteBody, @Nullable OutgoingBody body) {
        poolHandle.taint();
        client.completeExceptionallySafe(sink, t);
        if (response instanceof Response.Preparing) {
            // nothing took over the exchange: release everything here
            response = new Response.Finished(false);
            upload = Upload.Stopped.INSTANCE;
            if (body != null) {
                body.discard();
            }
            closeReplay();
            byteBody.close();
            poolHandle.release();
            // the response handling may have claimed the selection before it failed
            NettyHttpClient.releaseSelection(plan.selection());
        } else {
            // the response listener owns the exchange, even if it already ended (e.g. a
            // customizer failed it): it cancels the stream writer, releases the pool handle and
            // the selection and closes the replay body, once the connection is closed if it is
            // still running. It only releases the buffer if it was held back for a CONTINUE.
            if (body instanceof OutgoingBody.Buffered buffered && !plan.expectContinue()) {
                buffered.buf().release();
            }
            byteBody.close();
            poolHandle.channel().close();
        }
    }

    /**
     * Start the response listener on the connection and finalize the request headers, without
     * writing anything to the channel yet.
     *
     * @return {@code false} if the connection could not take the request. The request is then
     * already cleaned up and failed
     */
    private boolean startListener(OutgoingBody body) {
        HttpRequest nettyRequest = plan.nettyRequest();
        if (log.isTraceEnabled()) {
            HttpHeadersUtil.trace(log, nettyRequest.headers().names(), nettyRequest.headers()::getAll);
            if (body instanceof OutgoingBody.Buffered buffered) {
                client.traceBody("Request", buffered.buf());
            }
        }

        LoadBalancerSelection selection = plan.selection();
        if (selection != null) {
            // from here on, the response handling reports or releases the selection
            selection.claim();
        }
        try {
            // committed first: the listener may be called as soon as it is started. An available
            // body that is not held back stays with start(), which writes it right after this
            response = Response.Awaiting.INSTANCE;
            if (plan.expectContinue()) {
                upload = new Upload.Holding(body, null);
            } else if (body instanceof OutgoingBody.Streamed streamed) {
                upload = new Upload.Streaming(streamed.writer());
            } else {
                upload = Upload.Written.INSTANCE;
            }
            // the response handler is installed once per connection (PoolHandle.responseHandler),
            // this only sets the listener for this request
            poolHandle.responseHandler.startRequest(this);
        } catch (IllegalStateException e) {
            // the handler is gone (e.g. removed by a customizer) or still busy with the previous
            // request: the connection cannot be used
            response = new Response.Finished(false);
            upload = Upload.Stopped.INSTANCE;
            poolHandle.taint();
            body.discard();
            // the request is not sent, so it is not sent again either
            closeReplay();
            poolHandle.release();
            // the request is not sent, so the listener never reports the selection
            NettyHttpClient.releaseSelection(selection);
            client.completeExceptionallySafe(sink, client.decorate(new HttpClientException("Failed to send the request on the connection", e)));
            return false;
        } catch (Throwable t) {
            // the listener did not take over: start() releases everything
            response = Response.Preparing.INSTANCE;
            upload = Upload.Idle.INSTANCE;
            throw t;
        }
        // cancelling the exchange before the response arrives aborts the request: the connection
        // (HTTP/1) or the stream (HTTP/2) is closed, which also stops the request body. The flow
        // may be cancelled on any thread
        sink.onCancel(() -> poolHandle.channel().eventLoop().execute(this::cancel));
        // the customizers may end the exchange, e.g. by failing or closing the connection
        poolHandle.notifyRequestPipelineBuilt();

        HttpHeaders headers = nettyRequest.headers();
        OptionalLong length = plan.length();
        if (length.isPresent()) {
            headers.remove(HttpHeaderNames.TRANSFER_ENCODING);
            if (length.getAsLong() != 0 || permitsRequestBody(nettyRequest.method())) {
                headers.set(HttpHeaderNames.CONTENT_LENGTH, length.getAsLong());
            }
        } else {
            headers.remove(HttpHeaderNames.CONTENT_LENGTH);
            headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }

        if (!poolHandle.http2) {
            if (plan.requestedUpgrade() != null) {
                headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
            } else if (poolHandle.canReturn()) {
                headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
        }

        // read again: a customizer may have ended the exchange, which dropped the held body
        if (upload instanceof Upload.Holding holding) {
            // a server that ignores the expectation waits for the body: send it after a while anyway (RFC 9110 10.1.1).
            // The head is written right after this, on this event loop, before the timer can fire
            client.getConfiguration().getExpectContinueTimeout().ifPresent(timeout ->
                upload = new Upload.Holding(holding.body(), poolHandle.channel().eventLoop().schedule(this::sendHeldBody, timeout.toNanos(), TimeUnit.NANOSECONDS)));
        }
        return true;
    }

    /**
     * Write the request: the head and an available body, or the head and then the stream, or only
     * the head if the body waits for {@code 100 Continue}.
     */
    private void writeRequest(OutgoingBody body) {
        HttpRequest nettyRequest = plan.nettyRequest();
        NettyHttpClient.UploadListener uploadListener = plan.uploadListener();
        Channel channel = poolHandle.channel();
        // taken before the head is written, on the event loop: nothing of this request has reached
        // the transport yet
        TransportWriteTracker writeTracker = TransportWriteTracker.find(channel);
        long writeMark = writeTracker == null ? 0 : writeTracker.mark();
        if (body instanceof OutgoingBody.Buffered buffered) {
            if (!plan.expectContinue()) {
                uploadStarted(uploadListener);
                // it's a bit more efficient to use a full request for HTTP/2
                channel.writeAndFlush(new DefaultFullHttpRequest(
                    nettyRequest.protocolVersion(),
                    nettyRequest.method(),
                    nettyRequest.uri(),
                    buffered.buf(),
                    nettyRequest.headers(),
                    EmptyHttpHeaders.INSTANCE
                ), whenSent(requestWritePromise(channel, writeTracker, writeMark), uploadListener == null ? null : uploadListener.uploaded()));
            } else {
                channel.writeAndFlush(nettyRequest, requestWritePromise(channel, writeTracker, writeMark));
            }
        } else if (body instanceof OutgoingBody.Streamed streamed) {
            channel.writeAndFlush(nettyRequest, requestWritePromise(channel, writeTracker, writeMark));
            if (!plan.expectContinue()) {
                uploadStarted(uploadListener);
                streamed.writer().startWriting();
            }
        }
    }

    /**
     * Send the body held back for {@code 100 Continue}: the server answered, or the fallback
     * timer fired. Does nothing once the body was sent or dropped.
     */
    void sendHeldBody() {
        if (!(upload instanceof Upload.Holding holding)) {
            return;
        }
        holding.cancelFallback();
        NettyHttpClient.UploadListener uploadListener = plan.uploadListener();
        switch (holding.body()) {
            case OutgoingBody.Buffered buffered -> {
                upload = Upload.Written.INSTANCE;
                uploadStarted(uploadListener);
                Channel channel = poolHandle.channel();
                if (uploadListener == null) {
                    channel.writeAndFlush(new DefaultLastHttpContent(buffered.buf()), channel.voidPromise());
                } else {
                    channel.writeAndFlush(new DefaultLastHttpContent(buffered.buf())).addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            uploadListener.uploaded().run();
                        } else {
                            // like the void promise of the other case
                            channel.pipeline().fireExceptionCaught(future.cause());
                        }
                    });
                }
            }
            case OutgoingBody.Streamed streamed -> {
                upload = new Upload.Streaming(streamed.writer());
                uploadStarted(uploadListener);
                streamed.writer().startWriting();
            }
        }
    }

    /**
     * Drop the body held back for {@code 100 Continue}, if it is still held: the request was not
     * sent completely, so the connection cannot be reused.
     */
    private void dropHeldBody() {
        if (upload instanceof Upload.Holding holding) {
            upload = Upload.Stopped.INSTANCE;
            holding.cancelFallback();
            holding.body().discard();
            poolHandle.taint();
        }
    }

    /**
     * Stop the upload: drop a body still held back, or cancel the stream writer. A request that
     * was not sent completely leaves the connection unusable.
     */
    private void stopUpload() {
        if (upload instanceof Upload.Streaming streaming) {
            upload = Upload.Stopped.INSTANCE;
            StreamWriter streamWriter = streaming.writer();
            if (!streamWriter.isCompleted()) {
                // if there was an error, and we didn't fully write the request yet, the
                // connection cannot be reused
                poolHandle.taint();
            }
            streamWriter.cancel();
        } else {
            dropHeldBody();
        }
    }

    /**
     * Accept the final response: the body still held back for {@code 100 Continue} is dropped, a
     * streamed body goes on, the request can no longer be sent again, and the caller gets the
     * response unless it cancelled.
     *
     * @param head The response head
     * @param body The response body, owned by the exchange from here on
     */
    void acceptResponse(HttpResponse head, CloseableByteBody body) {
        // the final response arrived before 100 Continue, e.g. 417 Expectation Failed: the
        // server rejected the body, so it is not sent when the fallback timer fires later, while
        // the response body is still streaming
        dropHeldBody();
        response = new Response.Receiving(head.status().code());
        closeReplay();
        if (!HttpUtil.isKeepAlive(head)) {
            poolHandle.taint();
        }
        if (sink.isCancelled()) {
            // nobody takes the response of a cancelled exchange, and its outcome says nothing
            // about the instance
            reportOnce(null);
            body.close();
            return;
        }
        sink.complete(new NettyClientByteBodyResponse(head, body, client.conversionService()));
    }

    /**
     * Accept a {@code 101 Switching Protocols} response: the connection is handed to a duplex
     * handler that relays the new protocol, or the exchange fails if the server switched to a
     * protocol that was not offered.
     *
     * @param ctx  The handler context
     * @param head The response head
     * @return Whether the exchange took the connection over, see
     * {@link Http1ResponseHandler.ResponseListener#upgrade}
     */
    boolean acceptUpgrade(ChannelHandlerContext ctx, HttpResponse head) {
        String requestedUpgrade = plan.requestedUpgrade();
        if (requestedUpgrade == null) {
            return false;
        }
        String accepted = ExchangePlan.joinedValues(head.headers(), HttpHeaderNames.UPGRADE);
        if (accepted == null || !NettyHttpClient.isOffered(accepted, requestedUpgrade)) {
            // the server switched to something else than what was asked: not a connection to relay
            fail(ctx, new HttpClientException("The server switched the connection to protocol '" + accepted + "', but '" + requestedUpgrade + "' was offered"));
            finish(ctx);
            return true;
        }
        // the exchange ends with the switch: what the new protocol does is not counted
        reportOnce(null);
        closeReplay();
        // the connection belongs to the new protocol now, and is closed when that ends
        poolHandle.taint();
        if (sink.isCancelled()) {
            stopUpload();
            response = Response.Upgraded.INSTANCE;
            finish(ctx);
            return true;
        }
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.remove(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE);
        // a body still held for 100 Continue is not sent on the switched connection
        stopUpload();
        response = Response.Upgraded.INSTANCE;
        if (plan.activityTimeout() != null) {
            pipeline.addLast(new IdleStateHandler(0, 0, plan.activityTimeout().toNanos(), TimeUnit.NANOSECONDS));
        }
        // the pool handle goes to the duplex handler, which releases it when the connection closes
        RawDuplexHandler duplex = new RawDuplexHandler(poolHandle.channel(), poolHandle::release);
        // in place before the codec goes: the bytes of the new protocol the codec read together with the
        // 101 are passed on to the next handlers when it is removed, and must reach the duplex handler
        pipeline.addLast(RawDuplexHandler.NAME, duplex);
        for (String name : List.of(ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT, RequestReadIdleTimeoutHandler.NAME, ChannelPipelineCustomizer.HANDLER_HTTP_DECODER, ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC)) {
            if (pipeline.get(name) != null) {
                pipeline.remove(name);
            }
        }
        sink.complete(new NettyClientUpgradedResponse(head, duplex, client.conversionService()));
        return true;
    }

    /**
     * The caller cancelled the exchange. Runs on the event loop: the flow may be cancelled from
     * any thread, and {@link #startListener} moves that here. Before the response arrives, this
     * aborts the request: the connection (HTTP/1) or the stream (HTTP/2) is closed, which also
     * stops the request body and fails, then finishes, the exchange.
     */
    void cancel() {
        boolean responded = switch (response) {
            case Response.Receiving receiving -> true;
            case Response.Upgraded upgraded -> true;
            case Response.Finished finished -> finished.responded();
            default -> false;
        };
        if (!responded) {
            poolHandle.taint();
            poolHandle.channel().close();
        }
    }

    @Override
    public void fail(ChannelHandlerContext ctx, Throwable cause) {
        poolHandle.taint();
        Replay unused = replay;
        if (unused != null) {
            replay = null;
            if (!sink.isCancelled() && isConnectionClosedError(cause)) {
                // the server closed the connection while it was idle in the pool, so it cannot
                // have processed this request. Send it again once the dead connection is
                // released, in finish().
                pendingRetry = unused;
                return;
            }
            unused.close();
        }
        if (!sink.isCancelled()) {
            // nobody takes the error of a cancelled exchange, e.g. its closed connection
            LoadBalancerSelection selection = plan.selection();
            ServiceInstance instance = selection == null ? null : selection.instance();
            HttpClientException failure = client.handleResponseError(plan.request(), instance, cause);
            reportOnce(NettyHttpClient.failureOutcome(failure));
            client.completeExceptionallySafe(sink, failure);
        } else {
            reportOnce(null);
        }
    }

    /**
     * The response ended, failed, or the exchange ended on a protocol switch: release what the
     * exchange still owns and give the connection back. A request whose reused connection was
     * found closed is then sent again by the caller.
     *
     * @param ctx The handler context
     */
    @Override
    public void finish(ChannelHandlerContext ctx) {
        // the body ended, unless it failed, which was reported first
        reportResponse();
        boolean responded = response instanceof Response.Receiving || response instanceof Response.Upgraded;
        Replay retry = pendingRetry;
        pendingRetry = null;
        if (retry != null) {
            // a first attempt on a stale connection is not an outcome of the instance: the
            // selection goes on to the attempt that sends the request again
            reported = true;
        } else {
            // an exchange that ended without a response, e.g. cancelled, is released
            reportOnce(null);
        }
        // the body is still held if the exchange failed before any response arrived
        stopUpload();
        response = new Response.Finished(responded);
        // committed before the pool and the caller run: either may start the next exchange
        poolHandle.release();
        if (retry != null) {
            if (sink.isCancelled() || !sink.tryCompleteExceptionally(new NettyHttpClient.StaleConnectionException(retry.body()))) {
                retry.close();
                // nothing sends the request again
                NettyHttpClient.releaseSelection(plan.selection());
            }
        }
    }

    @Override
    public void complete(HttpResponse head, CloseableByteBody body) {
        acceptResponse(head, body);
    }

    @Override
    public boolean upgrade(ChannelHandlerContext ctx, HttpResponse head) {
        return acceptUpgrade(ctx, head);
    }

    @Override
    public void continueReceived(ChannelHandlerContext ctx) {
        sendHeldBody();
    }

    @Override
    public void bodyFailed(ChannelHandlerContext ctx, Throwable cause) {
        // the body fails for its consumer, which maps and decorates the cause
        reportOnce(NettyHttpClient.failureOutcome(cause));
    }

    @Override
    public void allowDiscard() {
        // the caller is done with the response before its body ended: the instance responded,
        // and what the connection does after that is not counted against it
        reportResponse();
    }

    @Override
    public void discardLimitReached() {
        // the rest of an abandoned body is too long to drain (e.g. an endless stream whose
        // downstream client went away): close the connection (HTTP/1) or reset the stream
        // (HTTP/2) instead of reading it
        poolHandle.taint();
        poolHandle.channel().close();
    }

    @Override
    public BodySizeLimits sizeLimits() {
        return client.sizeLimits();
    }

    @Override
    public boolean isHeadResponse() {
        return plan.nettyRequest().method().equals(HttpMethod.HEAD);
    }

    @Override
    public void writabilityChanged(ChannelHandlerContext ctx) {
        StreamWriter streamWriter = switch (upload) {
            case Upload.Holding holding -> holding.body() instanceof OutgoingBody.Streamed streamed ? streamed.writer() : null;
            case Upload.Streaming streaming -> streaming.writer();
            default -> null;
        };
        if (streamWriter != null) {
            streamWriter.channelWritabilityChanged();
        }
    }

    /**
     * Report the outcome of the exchange to the load balancer once: a failure before the response
     * or of its body, or else the status once the body ended or the caller let it go. An exchange
     * cancelled before its response, or that ended without an outcome, is released.
     *
     * @param outcome The outcome, or {@code null} to release the selection
     */
    private void reportOnce(LoadBalancer.@Nullable Outcome outcome) {
        if (!reported) {
            reported = true;
            if (outcome != null) {
                NettyHttpClient.report(plan.selection(), outcome);
            } else {
                NettyHttpClient.releaseSelection(plan.selection());
            }
        }
    }

    private void reportResponse() {
        if (response instanceof Response.Receiving receiving) {
            reportOnce(receiving.code() >= 500 ? LoadBalancer.Outcome.SERVER_ERROR : LoadBalancer.Outcome.SUCCESS);
        }
    }

    /**
     * The request can no longer be sent again: close the copy of its body.
     */
    private void closeReplay() {
        Replay unused = replay;
        if (unused != null) {
            replay = null;
            unused.close();
        }
    }

    /**
     * Whether a failure before the response headers means that the connection was closed or
     * broken, as opposed to e.g. a timeout or an invalid response.
     *
     * @param cause The failure
     * @return {@code true} iff the connection was closed
     */
    private static boolean isConnectionClosedError(Throwable cause) {
        if (cause instanceof UnprocessedRequestException unprocessed) {
            // the request head could not be written because the connection was closed
            return unprocessed.getReason() == UnprocessedRequestException.Reason.CLOSED_BEFORE_WRITE;
        }
        return cause instanceof ResponseClosedException || cause instanceof IOException;
    }

    private static boolean requiresRequestBody(HttpMethod method) {
        return method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT) || method.equals(HttpMethod.PATCH) || method.equals(HttpMethod.QUERY);
    }

    private static boolean permitsRequestBody(HttpMethod method) {
        return requiresRequestBody(method)
            || method.equals(HttpMethod.OPTIONS)
            || method.equals(HttpMethod.DELETE);
    }

    private static void uploadStarted(NettyHttpClient.@Nullable UploadListener uploadListener) {
        if (uploadListener != null) {
            uploadListener.started().run();
        }
    }

    private static ChannelPromise whenSent(ChannelPromise promise, @Nullable Runnable onSent) {
        if (onSent != null) {
            promise.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    onSent.run();
                }
            });
        }
        return promise;
    }

    /**
     * The promise of the write of the request head, or of the full request when the body is
     * available. Like a void promise, a failure is reported to the pipeline, so that the response
     * handler fails the exchange.
     * <p>A write that fails with a {@link ClosedChannelException} is reported as an
     * {@link UnprocessedRequestException}, so that the caller can send the request again on
     * another connection, only when the tracker says that nothing was handed to the transport
     * since the mark: the connection was closed before any byte of the request left. The
     * exception alone does not tell: the transport fails a write with the same exception when the
     * connection closes after part of the message was sent, e.g. a large full request of which
     * the server read the head and some of the body before it stopped reading and closed. Such a
     * request may have been processed, so its failure is a {@link ResponseClosedException}
     * without headers, as when the connection closes while the response is awaited. Without a
     * tracker, no failure is reported as unprocessed. The body chunks of a streamed request are
     * written after the head with their own promises and are never reported as unprocessed.
     *
     * @param channel The channel
     * @param tracker The write tracker of the connection, or {@code null} if it has none
     * @param mark    The {@link TransportWriteTracker#mark() mark} taken before the request was
     *                written
     * @return The promise
     */
    private static ChannelPromise requestWritePromise(Channel channel, @Nullable TransportWriteTracker tracker, long mark) {
        ChannelPromise promise = channel.newPromise();
        promise.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                return;
            }
            Throwable cause = future.cause();
            if (cause instanceof ClosedChannelException) {
                if (tracker != null && !tracker.flushedSince(mark)) {
                    cause = new UnprocessedRequestException(UnprocessedRequestException.Reason.CLOSED_BEFORE_WRITE, "Connection closed before the request was written", cause);
                } else {
                    ResponseClosedException closed = new ResponseClosedException("Connection closed while the request was written, before the response was received", false);
                    closed.initCause(cause);
                    cause = closed;
                }
            }
            channel.pipeline().fireExceptionCaught(cause);
        });
        return promise;
    }


    /**
     * The phase of an exchange, combined from its {@link Upload} and {@link Response} states.
     */
    enum Phase {
        /**
         * The listener did not take over yet.
         */
        PREPARING,
        /**
         * The head is sent, the body is held back until the server answers {@code 100 Continue}.
         */
        WAITING_FOR_CONTINUE,
        /**
         * The request is written, or being written, and the response is awaited.
         */
        SENDING,
        /**
         * The response head arrived and its body is received. A streamed request body may still
         * be written.
         */
        RECEIVING,
        /**
         * The connection switched to another protocol.
         */
        UPGRADING,
        /**
         * The exchange ended and released what it owned.
         */
        FINISHED
    }

    /**
     * The upload of the request body. Each state carries what it owns.
     */
    sealed interface Upload {

        /**
         * The listener did not take over yet: {@link #start} owns the body.
         */
        enum Idle implements Upload {
            INSTANCE
        }

        /**
         * The head is sent and the body is held back until the server answers {@code 100
         * Continue}, a final response drops it, or the fallback timer sends it anyway.
         *
         * @param body     The held body
         * @param fallback The timer that sends the body without an answer, or {@code null}
         */
        record Holding(OutgoingBody body, @Nullable ScheduledFuture<?> fallback) implements Upload {
            void cancelFallback() {
                if (fallback != null) {
                    fallback.cancel(false);
                }
            }
        }

        /**
         * A streamed body is written, possibly while the response is already received.
         *
         * @param writer The writer, cancelled when the upload stops
         */
        record Streaming(StreamWriter writer) implements Upload {
        }

        /**
         * An available body is written with the head, or after {@code 100 Continue}. The
         * connection owns it once written.
         */
        enum Written implements Upload {
            INSTANCE
        }

        /**
         * The body was dropped, or its writer cancelled.
         */
        enum Stopped implements Upload {
            INSTANCE
        }
    }

    /**
     * The response of the exchange.
     */
    sealed interface Response {

        /**
         * The listener did not take over yet.
         */
        enum Preparing implements Response {
            INSTANCE
        }

        /**
         * The listener took over and the response head is awaited.
         */
        enum Awaiting implements Response {
            INSTANCE
        }

        /**
         * The response head arrived and its body is received.
         *
         * @param code The status of the response
         */
        record Receiving(int code) implements Response {
        }

        /**
         * The connection switched to another protocol: it belongs to the duplex handler, or was
         * released if the exchange was cancelled.
         */
        enum Upgraded implements Response {
            INSTANCE
        }

        /**
         * The exchange ended and released what it owned.
         *
         * @param responded Whether a response or a protocol switch arrived
         */
        record Finished(boolean responded) implements Response {
        }
    }

    /**
     * The body of the request, as it is written to the connection.
     */
    sealed interface OutgoingBody {

        /**
         * Release the body without sending it.
         */
        void discard();

        /**
         * An available body.
         *
         * @param buf The body, released when it is written or discarded
         */
        record Buffered(ByteBuf buf) implements OutgoingBody {
            @Override
            public void discard() {
                buf.release();
            }
        }

        /**
         * A streamed body.
         *
         * @param writer The writer, which starts writing once the body is sent
         */
        record Streamed(StreamWriter writer) implements OutgoingBody {
            @Override
            public void discard() {
                writer.cancel();
            }
        }
    }

    /**
     * The copy of a request body that is kept so that the request can be sent again if its reused
     * connection turns out to be closed already.
     *
     * @param body The copy, or {@code null} for an empty body
     */
    record Replay(@Nullable CloseableAvailableByteBody body) {
        void close() {
            if (body != null) {
                body.close();
            }
        }
    }
}
