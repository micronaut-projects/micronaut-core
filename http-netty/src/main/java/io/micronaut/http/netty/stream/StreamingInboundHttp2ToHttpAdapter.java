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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Implementation of {@link Http2EventAdapter} that allows streaming requests for servers and responses for clients by
 * establishing a processor that emits chunks as {@link HttpContent}.
 *
 * This implementation does not buffer the data. If you need data buffering a {@link io.netty.handler.flow.FlowControlHandler}
 * can be placed after this implementation so that downstream handlers can control flow.
 *
 * Based on code in {@link InboundHttp2ToHttpAdapter}.
 *
 * @author graemerocher
 * @since 2.0
 */
public class StreamingInboundHttp2ToHttpAdapter extends Http2EventAdapter {
    protected final Http2Connection connection;
    protected final boolean validateHttpHeaders;
    private final int maxContentLength;
    private final Http2Connection.PropertyKey messageKey;
    private final boolean propagateSettings;
    private final Http2Connection.PropertyKey dataReadKey;

    /**
     * Default constructor.
     * @param connection The connection
     * @param maxContentLength The max content length
     * @param validateHttpHeaders Whether to validate headers
     * @param propagateSettings Whether to propagate settings
     */
    public StreamingInboundHttp2ToHttpAdapter(Http2Connection connection, int maxContentLength,
                                                 boolean validateHttpHeaders, boolean propagateSettings) {

        if (maxContentLength <= 0) {
            throw new IllegalArgumentException("maxContentLength: " + maxContentLength + " (expected: > 0)");
        }
        this.connection = checkNotNull(connection, "connection");
        this.maxContentLength = maxContentLength;
        this.validateHttpHeaders = validateHttpHeaders;
        this.propagateSettings = propagateSettings;
        messageKey = connection.newKey();
        dataReadKey = connection.newKey();
    }

    /**
     * Default constructor.
     * @param connection The connection
     * @param maxContentLength The max content length
     */
    public StreamingInboundHttp2ToHttpAdapter(Http2Connection connection, int maxContentLength) {

        this(connection, maxContentLength, true, true);
    }

    /**
     * The stream is out of scope for the HTTP message flow and will no longer be tracked.
     *
     * @param stream The stream to remove associated state with
     */
    protected final void removeMessage(Http2Stream stream) {
        stream.removeProperty(messageKey);
    }

    /**
     * Get the {@link FullHttpMessage} associated with {@code stream}.
     * @param stream The stream to get the associated state from
     * @return The {@link FullHttpMessage} associated with {@code stream}.
     */
    protected final HttpMessage getMessage(Http2Stream stream) {
        return (HttpMessage) stream.getProperty(messageKey);
    }

    /**
     * Make {@code message} be the state associated with {@code stream}.
     * @param stream The stream which {@code message} is associated with.
     * @param message The message which contains the HTTP semantics.
     */
    protected final void putMessage(Http2Stream stream, HttpMessage message) {
        stream.setProperty(messageKey, message);
    }

    @Override
    public void onStreamRemoved(Http2Stream stream) {
        removeMessage(stream);
    }

    /**
     * fire a channel read event.
     *
     * @param ctx The context to fire the event on
     * @param msg The message to send
     * @param stream the stream of the message which is being fired
     */
    protected void fireChannelRead(ChannelHandlerContext ctx, HttpContent msg,
                                   Http2Stream stream) {
        ctx.fireChannelRead(msg);
    }

    /**
     * fire a channel read event.
     *
     * @param ctx The context to fire the event on
     * @param msg The message to send
     * @param stream the stream of the message which is being fired
     */
    protected void fireChannelRead(ChannelHandlerContext ctx, HttpMessage msg,
                                   Http2Stream stream) {
        if (connection.isServer()) {
            // the event has to come after the flow control handler to avoid buffering
            // there may be a better way to do this.
            final ChannelHandlerContext context = ctx.pipeline().context("flow-control-handler");
            if (context != null) {
                context.fireChannelRead(msg);
            } else {
                ctx.fireChannelRead(msg);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Create a new {@link FullHttpMessage} based upon the current connection parameters.
     *
     *
     * @param ctx The channel context
     * @param stream The stream to create a message for
     * @param headers The headers associated with {@code stream}
     * @param validateHttpHeaders
     * <ul>
     * <li>{@code true} to validate HTTP headers in the http-codec</li>
     * <li>{@code false} not to validate HTTP headers in the http-codec</li>
     * </ul>
     * @throws Http2Exception thrown if an error occurs creating the request
     * @return A new {@link StreamedHttpMessage}
     */
    protected HttpMessage newMessage(
            ChannelHandlerContext ctx,
            Http2Stream stream,
            Http2Headers headers,
            boolean validateHttpHeaders)
            throws Http2Exception {
        return connection.isServer() ? HttpConversionUtil.toHttpRequest(stream.id(), headers, validateHttpHeaders) :
                HttpConversionUtil.toHttpResponse(stream.id(), headers, validateHttpHeaders);
    }

    /**
     * Provides translation between HTTP/2 and HTTP header objects while ensuring the stream
     * is in a valid state for additional headers.
     *
     * @param ctx The context for which this message has been received.
     * Used to send informational header if detected.
     * @param stream The stream the {@code headers} apply to
     * @param headers The headers to process
     * @param allowAppend
     * <ul>
     * <li>{@code true} if headers will be appended if the stream already exists.</li>
     * <li>if {@code false} and the stream already exists this method returns {@code null}.</li>
     * </ul>
     * @param appendToTrailer
     * <ul>
     * <li>{@code true} if a message {@code stream} already exists then the headers
     * should be added to the trailing headers.</li>
     * <li>{@code false} then appends will be done to the initial headers.</li>
     * </ul>
     * @return The object used to track the stream corresponding to {@code stream}. {@code null} if
     *         {@code allowAppend} is {@code false} and the stream already exists.
     * @throws Http2Exception If the stream id is not in the correct state to process the headers request
     */
    protected HttpMessage processHeadersBegin(ChannelHandlerContext ctx, Http2Stream stream, Http2Headers headers,
                                                      boolean allowAppend, boolean appendToTrailer) throws Http2Exception {
        HttpMessage msg = getMessage(stream);
        if (msg == null) {
            msg = newMessage(ctx, stream, headers, validateHttpHeaders);
            putMessage(stream, msg);
        } else if (allowAppend) {
            HttpConversionUtil.addHttp2ToHttpHeaders(
                    stream.id(),
                    headers,
                    msg.headers(),
                    HttpVersion.HTTP_1_1,
                    appendToTrailer,
                    msg instanceof HttpRequest
            );
        } else {
            msg = null;
        }
        return msg;
    }

    /**
     * After HTTP/2 headers have been processed by {@link #processHeadersBegin} this method either
     * sends the result up the pipeline or retains the message for future processing.
     *
     * @param ctx The context for which this message has been received
     * @param stream The stream the {@code objAccumulator} corresponds to
     * @param msg The object which represents all headers/data for corresponding to {@code stream}
     * @param endOfStream {@code true} if this is the last event for the stream
     */
    private void processHeadersEnd(
            ChannelHandlerContext ctx,
            Http2Stream stream,
            HttpMessage msg,
            boolean endOfStream) {
        if (endOfStream) {
            if (connection.isServer()) {
                HttpRequest existing = (HttpRequest) msg;
                msg = new DefaultFullHttpRequest(
                        HttpVersion.HTTP_1_1,
                        existing.method(),
                        existing.uri(),
                        new EmptyByteBuf(ctx.alloc()),
                        existing.headers(),
                        EmptyHttpHeaders.INSTANCE);
            } else {
                HttpResponse existing = (HttpResponse) msg;
                msg = new DefaultFullHttpResponse(
                        existing.protocolVersion(),
                        existing.status(),
                        new EmptyByteBuf(ctx.alloc()),
                        existing.headers(),
                        EmptyHttpHeaders.INSTANCE
                );
            }
            // no more data after headers to just fire as a regular http request
            HttpUtil.setContentLength(msg, 0);
            fireChannelRead(ctx, msg, stream);
        } else {
            if (!msg.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                HttpUtil.setTransferEncodingChunked(msg, true);
            }
            fireChannelRead(ctx, msg, stream);
        }
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
            throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        HttpMessage msg = getMessage(stream);
        int dataRead = getDataRead(stream);
        if (msg == null) {
            throw connectionError(PROTOCOL_ERROR, "Data Frame received for unknown stream id %d", streamId);
        }

        final int dataReadableBytes = data.readableBytes();
        if (dataRead > maxContentLength - dataReadableBytes) {
            throw connectionError(INTERNAL_ERROR,
                    "Content length exceeded max of %d for stream id %d", maxContentLength, streamId);

        }

        dataRead += dataReadableBytes;
        stream.setProperty(dataReadKey, dataRead);

        if (endOfStream) {
            // end of stream, emits a LastHttpContent
            // will be released by HttpStreamsHandler
            final DefaultLastHttpContent content = new DefaultLastHttpContent(data.retain());
            fireChannelRead(ctx, content, stream);
        } else {
            // will be released by HttpStreamsHandler
            final DefaultHttpContent content = new DefaultHttpContent(data.retain());
            fireChannelRead(ctx, content, stream);
        }

        // All bytes have been processed.
        return dataReadableBytes + padding;
    }

    private int getDataRead(Http2Stream stream) {
        final Object demand = stream.getProperty(dataReadKey);
        if (demand instanceof Number) {
            return ((Number) demand).intValue();
        }
        return 0;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                              boolean endOfStream) throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        HttpMessage msg =
                processHeadersBegin(ctx, stream, headers, true, true);
        if (msg != null) {
            processHeadersEnd(ctx, stream, msg, endOfStream);
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                              short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        HttpMessage msg = processHeadersBegin(ctx, stream, headers, true, true);
        if (msg != null) {
            // Add headers for dependency and weight.
            // See https://github.com/netty/netty/issues/5866
            if (streamDependency != Http2CodecUtil.CONNECTION_STREAM_ID) {
                msg.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(),
                        streamDependency);
            }
            msg.headers().setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), weight);

            processHeadersEnd(ctx, stream, msg, endOfStream);
        }
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
        Http2Stream stream = connection.stream(streamId);
        HttpMessage msg = getMessage(stream);
        if (msg != null) {
            onRstStreamRead(stream, msg);
        }
        ctx.fireExceptionCaught(Http2Exception.streamError(streamId, Http2Error.valueOf(errorCode),
                "HTTP/2 to HTTP layer caught stream reset"));
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                  Http2Headers headers, int padding) throws Http2Exception {
        // A push promise should not be allowed to add headers to an existing stream
        Http2Stream promisedStream = connection.stream(promisedStreamId);
        if (headers.status() == null) {
            // A PUSH_PROMISE frame has no Http response status.
            // https://tools.ietf.org/html/rfc7540#section-8.2.1
            // Server push is semantically equivalent to a server responding to a
            // request; however, in this case, that request is also sent by the
            // server, as a PUSH_PROMISE frame.
            headers.status(OK.codeAsText());
        }
        HttpMessage msg = processHeadersBegin(ctx, promisedStream, headers, false, false);
        if (msg == null) {
            throw connectionError(PROTOCOL_ERROR, "Push Promise Frame received for pre-existing stream id %d",
                    promisedStreamId);
        }

        msg.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_PROMISE_ID.text(), streamId);
        msg.headers().setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(),
                Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT);

        processHeadersEnd(ctx, promisedStream, msg, false);
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
        if (propagateSettings) {
            // Provide an interface for non-listeners to capture settings
            ctx.fireChannelRead(settings);
        }
    }

    /**
     * Called if a {@code RST_STREAM} is received but we have some data for that stream.
     *
     * @param stream The stream
     * @param msg The message
     */
    protected void onRstStreamRead(Http2Stream stream, HttpMessage msg) {
        removeMessage(stream);
    }
}
