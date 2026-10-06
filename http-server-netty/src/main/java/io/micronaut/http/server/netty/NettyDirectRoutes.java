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
package io.micronaut.http.server.netty;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.handler.OutboundAccess;
import io.micronaut.http.server.netty.handler.PipeliningServerHandler;
import io.micronaut.http.server.util.HttpDateHeader;
import io.micronaut.web.router.direct.DirectRequest;
import io.micronaut.web.router.direct.DirectRouteLookup;
import io.micronaut.web.router.direct.PendingResponse;
import io.micronaut.web.router.uri.UriUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Answers the direct routes of the application in the Netty pipeline: {@link RoutingInBoundHandler}
 * looks up the Netty request it received, an HTTP/1.1 request or an HTTP/2 or HTTP/3 stream
 * converted to one, before it creates the {@link NettyHttpRequest}. A request a direct route
 * matches is answered with the status, the headers and the body of the response the route
 * created for it, and the headers the server is configured to add to every response, the
 * {@code Date} and {@code Server} headers, when the route did not set them. The
 * {@code Content-Length} of the body frames it, and the response is written by the
 * {@link OutboundAccess} of the request like any response, so keep-alive, pipelining and
 * {@code HEAD} are handled as for the other responses. The body of the request is discarded. No
 * filter, route, request event or request scope sees the request.
 *
 * <p>A synchronous route is answered on the event loop, and its body written there: a blocking
 * message body writer is refused, with {@code 500}. An asynchronous route, see
 * {@link PendingResponse}, holds the request, and its body, until the stage of its
 * response completes. Its body is then written on the event loop, unless its writer blocks: a
 * blocking writer runs on the thread that completed the stage, e.g. the executor of the route,
 * or on the IO executor when that thread is an event loop. The response is written by the event
 * loop, in the order of the requests of the connection. A stage completed
 * with {@code null} continues the request as an ordinary request, with its body untouched. The
 * stage is cancelled when the request is abandoned: the connection closes, or its HTTP/2 stream
 * is reset or closed, see {@link OutboundAccess#onAbandoned(Runnable)}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class NettyDirectRoutes {

    private static final Logger LOG = LoggerFactory.getLogger(NettyDirectRoutes.class);

    /**
     * The responses the routes create: the server writes them without converting them.
     */
    private static final HttpResponseFactory RESPONSES = new NettyHttpResponseFactory();

    private final DirectRouteLookup routes;
    private final NettyHttpServerConfiguration configuration;
    private final MessageBodyHandlerRegistry bodyHandlers;
    private final Supplier<? extends Executor> ioExecutor;
    private final OrdinaryRequests ordinary;
    /**
     * Whether to add the {@code Date} header, see {@code micronaut.server.date-header}.
     */
    private final boolean dateHeader;
    /**
     * The {@code Server} header to add, see {@code micronaut.server.server-header}, or {@code null}.
     */
    private final @Nullable String serverHeader;
    /**
     * The message body writers of the bodies that are neither bytes nor text, looked up once per
     * type and media type.
     */
    private final Map<WriterKey, ResolvedWriter> writers = new ConcurrentHashMap<>();

    private NettyDirectRoutes(DirectRouteLookup routes,
                              NettyHttpServerConfiguration configuration,
                              MessageBodyHandlerRegistry bodyHandlers,
                              Supplier<? extends Executor> ioExecutor,
                              OrdinaryRequests ordinary) {
        this.routes = routes;
        this.configuration = configuration;
        this.bodyHandlers = bodyHandlers;
        this.ioExecutor = ioExecutor;
        this.ordinary = ordinary;
        this.dateHeader = configuration.isDateHeader();
        this.serverHeader = configuration.getServerHeader().orElse(null);
    }

    /**
     * The direct routes of an application.
     *
     * @param beanContext   The bean context of the server
     * @param configuration The configuration of the server, which decodes the query like the
     *                      requests it creates
     * @param bodyHandlers  The message body writers of the server
     * @param ioExecutor    The executor of a blocking message body writer of an asynchronous
     *                      route whose stage completes on an event loop
     * @param ordinary      Handles a request an asynchronous route declined
     * @return The direct routes, or {@code null} if the application has none: the requests are
     * not looked up
     */
    static @Nullable NettyDirectRoutes of(BeanContext beanContext,
                                          NettyHttpServerConfiguration configuration,
                                          MessageBodyHandlerRegistry bodyHandlers,
                                          Supplier<? extends Executor> ioExecutor,
                                          OrdinaryRequests ordinary) {
        DirectRouteLookup routes = beanContext.findBean(DirectRouteLookup.class).orElse(null);
        return routes == null || routes.isEmpty() ? null : new NettyDirectRoutes(routes, configuration, bodyHandlers, ioExecutor, ordinary);
    }

    /**
     * Answer a request if a direct route matches it.
     *
     * @param ctx            The context of the channel
     * @param request        The request as it was received
     * @param body           The body of the request, closed if the request is answered
     * @param outboundAccess Writes the response
     * @return Whether a direct route answered the request, or will answer it or hand it to
     * {@link OrdinaryRequests} when its asynchronous response is complete. A request the
     * decoder failed on, or whose target or query is not a valid URI, is never answered by a
     * direct route: the ordinary path answers it, with its error
     */
    boolean answer(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
        if (request.decoderResult().isFailure()) {
            // e.g. a malformed or too long header: the ordinary path answers 400 or 413, and closes the connection
            return false;
        }
        NettyDirectRequest directRequest = new NettyDirectRequest(ctx, request, configuration);
        io.micronaut.http.HttpResponse<?> direct;
        try {
            direct = routes.find(directRequest, RESPONSES);
        } catch (InvalidRequestException e) {
            // the target or the query is not a valid URI, read before any route runs: the
            // ordinary path answers 400, with the body untouched
            return false;
        } catch (Throwable e) {
            // an Error too: the request is answered
            LOG.error("The direct routes failed to match {} {}: {}", request.method(), request.uri(), e.getMessage(), e);
            body.close();
            writeServerError(outboundAccess);
            return true;
        }
        if (direct == null) {
            // no direct route, or the one that matched declined: the body is untouched
            return false;
        }
        if (direct instanceof PendingResponse pending) {
            answerAsync(ctx, request, pending.stage(), body, outboundAccess);
            return true;
        }
        // the route never reads it
        body.close();
        ByteBuf content;
        HttpResponse head;
        try {
            MutableHttpResponse<?> response = mutable(direct);
            // written on the event loop: a blocking writer is refused
            content = content(ctx, response, writer(response, false));
            try {
                head = NettyMutableHttpResponse.toNoBodyResponse(response);
            } catch (Throwable e) {
                content.release();
                throw e;
            }
        } catch (Throwable e) {
            LOG.error("The direct route of {} {} failed to write its response: {}", request.method(), request.uri(), e.getMessage(), e);
            writeServerError(outboundAccess);
            return true;
        }
        write(ctx, request, head, content, outboundAccess);
        return true;
    }

    /**
     * Answer a request an asynchronous route matched, and started: the request is held until the
     * stage of its response completes.
     */
    private void answerAsync(ChannelHandlerContext ctx,
                             HttpRequest request,
                             CompletionStage<io.micronaut.http.@Nullable HttpResponse<?>> stage,
                             CloseableByteBody body,
                             OutboundAccess outboundAccess) {
        // the connection closes, or the HTTP/2 stream of the request is reset or closed
        Runnable cancelOnAbandon = outboundAccess.onAbandoned(() -> cancel(stage));
        stage.whenComplete((response, error) -> {
            // on the thread that completed the stage
            if (error != null || response == null) {
                onEventLoop(ctx, () -> complete(ctx, request, body, outboundAccess, cancelOnAbandon, response, error, null));
                return;
            }
            MutableHttpResponse<?> mutable;
            ResolvedWriter writer;
            try {
                mutable = mutable(response);
                writer = writer(mutable, true);
            } catch (Throwable e) {
                onEventLoop(ctx, () -> complete(ctx, request, body, outboundAccess, cancelOnAbandon, response, e, null));
                return;
            }
            Runnable prepare = () -> {
                Prepared prepared;
                try {
                    prepared = prepare(ctx, request, mutable, writer);
                } catch (Throwable t) {
                    // e.g. an allocation error
                    onEventLoop(ctx, () -> complete(ctx, request, body, outboundAccess, cancelOnAbandon, response, t, null));
                    return;
                }
                onEventLoop(ctx, () -> complete(ctx, request, body, outboundAccess, cancelOnAbandon, response, null, prepared));
            };
            if (writer == null || !writer.blocking()) {
                // bytes, text and the writers that do not block are written on the event loop
                onEventLoop(ctx, prepare);
            } else if (Thread.currentThread() instanceof FastThreadLocalThread) {
                // a blocking writer never runs on an event loop, e.g. of a client that completed the stage
                try {
                    ioExecutor.get().execute(prepare);
                } catch (RuntimeException e) {
                    onEventLoop(ctx, () -> complete(ctx, request, body, outboundAccess, cancelOnAbandon, response, e, null));
                }
            } else {
                // e.g. the executor of the route
                prepare.run();
            }
        });
    }

    /**
     * Complete a request held for an asynchronous route, on the event loop.
     *
     * @param prepared The response to write, or {@code null} if it could not be written, or the
     *                 route declined the request
     */
    private void complete(ChannelHandlerContext ctx,
                          HttpRequest request,
                          CloseableByteBody body,
                          OutboundAccess outboundAccess,
                          Runnable cancelOnAbandon,
                          io.micronaut.http.@Nullable HttpResponse<?> response,
                          @Nullable Throwable error,
                          @Nullable Prepared prepared) {
        cancelOnAbandon.run();
        if (error == null && response == null) {
            // declined: the request continues with its body untouched
            ordinary.accept(ctx, request, body, outboundAccess);
            return;
        }
        body.close();
        if (error != null) {
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (cause instanceof CancellationException) {
                LOG.debug("The direct route of {} {} was cancelled", request.method(), request.uri());
            } else {
                LOG.error("The direct route of {} {} failed: {}", request.method(), request.uri(), cause.getMessage(), cause);
            }
            writeServerError(outboundAccess);
        } else if (prepared != null) {
            write(ctx, request, prepared.head(), prepared.content(), outboundAccess);
        } else {
            writeServerError(outboundAccess);
        }
    }

    private static void onEventLoop(ChannelHandlerContext ctx, Runnable task) {
        if (ctx.executor().inEventLoop()) {
            task.run();
        } else {
            ctx.executor().execute(task);
        }
    }

    private static void cancel(CompletionStage<?> stage) {
        try {
            stage.toCompletableFuture().cancel(false);
        } catch (UnsupportedOperationException e) {
            LOG.debug("The stage of a direct route cannot be cancelled: {}", e.getMessage());
        }
    }

    private static MutableHttpResponse<?> mutable(io.micronaut.http.HttpResponse<?> response) {
        // a response of the Netty factory is written as it is: its status and its headers
        return response instanceof MutableHttpResponse<?> mutable ? mutable : response.toMutableResponse();
    }

    /**
     * The head and the content of the response of an asynchronous route.
     *
     * @return The response, or {@code null} if it cannot be written
     */
    private @Nullable Prepared prepare(ChannelHandlerContext ctx, HttpRequest request, MutableHttpResponse<?> response, @Nullable ResolvedWriter writer) {
        try {
            ByteBuf content = content(ctx, response, writer);
            HttpResponse head;
            try {
                head = NettyMutableHttpResponse.toNoBodyResponse(response);
            } catch (Throwable e) {
                content.release();
                throw e;
            }
            return new Prepared(head, content);
        } catch (Throwable e) {
            LOG.error("The direct route of {} {} failed to write its response: {}", request.method(), request.uri(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Write a response, on the event loop.
     */
    private void write(ChannelHandlerContext ctx, HttpRequest request, HttpResponse head, ByteBuf content, OutboundAccess outboundAccess) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Direct response {} - {} {}", head.status().code(), request.method(), request.uri());
        }
        HttpHeaders headers = head.headers();
        addConfiguredHeaders(headers);
        // the framing of the body: the Content-Length of the body is set by the outbound handler
        headers.remove(HttpHeaderNames.TRANSFER_ENCODING);
        boolean headRequest = HttpMethod.HEAD.equals(request.method());
        if (!PipeliningServerHandler.canHaveBody(head.status())) {
            // e.g. 204 or 304: never a body, nor a Content-Length, whatever the route set
            headers.remove(HttpHeaderNames.CONTENT_LENGTH);
            content.release();
            if (headRequest) {
                outboundAccess.writeHeadResponse(head);
            } else {
                outboundAccess.write(head, NettyByteBodyFactory.empty());
            }
        } else if (headRequest) {
            if (content.isReadable() || !headers.contains(HttpHeaderNames.CONTENT_LENGTH)) {
                // the length of the GET response, or the one a HEAD route without a body declares
                headers.set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            }
            content.release();
            outboundAccess.writeHeadResponse(head);
        } else {
            outboundAccess.write(head, content.isReadable()
                ? new NettyByteBodyFactory(ctx.channel()).adapt(content)
                : releaseEmpty(content));
        }
    }

    private static CloseableByteBody releaseEmpty(ByteBuf content) {
        content.release();
        return NettyByteBodyFactory.empty();
    }

    private void writeServerError(OutboundAccess outboundAccess) {
        DefaultHttpResponse error = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        addConfiguredHeaders(error.headers());
        error.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        outboundAccess.write(error, NettyByteBodyFactory.empty());
    }

    /**
     * Add the headers the server is configured to add to every response, like the ordinary
     * responses get them: the {@code Date} header, formatted at most once per second, and the
     * {@code Server} header, unless the route set them.
     */
    private void addConfiguredHeaders(HttpHeaders headers) {
        if (dateHeader && !headers.contains(HttpHeaderNames.DATE)) {
            headers.set(HttpHeaderNames.DATE, HttpDateHeader.now());
        }
        String server = serverHeader;
        if (server != null && !headers.contains(HttpHeaderNames.SERVER)) {
            headers.set(HttpHeaderNames.SERVER, server);
        }
    }

    /**
     * The message body writer of the body of a response, if it is neither bytes nor text: the
     * writer of the content type of the response, {@code application/json} if it has none,
     * which it then gets.
     *
     * @param allowBlocking Whether a blocking writer may write the body: only for an
     *                      asynchronous route, whose body is not written on the event loop
     * @return The writer, or {@code null} if the body is empty, bytes or text
     */
    private @Nullable ResolvedWriter writer(MutableHttpResponse<?> response, boolean allowBlocking) {
        Object body = response.body();
        if (body == null || body instanceof byte[] || body instanceof java.nio.ByteBuffer || body instanceof ByteBuf
            || body instanceof io.micronaut.core.io.buffer.ByteBuffer<?> || body instanceof CharSequence) {
            return null;
        }
        MediaType mediaType = response.getContentType().orElse(null);
        if (mediaType == null) {
            // like a route that produces JSON by default
            mediaType = MediaType.APPLICATION_JSON_TYPE;
            response.contentType(mediaType);
        }
        Class<?> type = body.getClass();
        ResolvedWriter resolved = writers.computeIfAbsent(new WriterKey(type, mediaType), key -> {
            @SuppressWarnings("unchecked")
            Argument<Object> argument = (Argument<Object>) Argument.of(key.type());
            MessageBodyWriter<Object> writer = bodyHandlers.findWriter(argument, List.of(key.mediaType()))
                .orElseThrow(() -> new CodecException("No message body writer for a body of type " + key.type().getName()
                    + " and media type " + key.mediaType() + " of a direct route"));
            return new ResolvedWriter(argument, key.mediaType(), writer.createSpecific(argument), writer.isBlocking());
        });
        if (resolved.blocking() && !allowBlocking) {
            throw new CodecException("The message body writer of a body of type " + type.getName() + " blocks: "
                + "a synchronous direct route is written on the event loop. Run the route on an executor with executeOn(...), "
                + "declare an asynchronous direct route, or declare an ordinary route");
        }
        return resolved;
    }

    /**
     * The body of a response: bytes and text are wrapped or encoded as they are, a buffer of the
     * server is written once, and any other body is written by its message body writer.
     */
    private static ByteBuf content(ChannelHandlerContext ctx, MutableHttpResponse<?> response, @Nullable ResolvedWriter writer) {
        Object body = response.body();
        if (body == null) {
            return Unpooled.EMPTY_BUFFER;
        }
        if (writer != null) {
            return (ByteBuf) writer.writer().writeTo(writer.type(), writer.mediaType(), body, response.getHeaders(),
                new NettyByteBufferFactory(ctx.alloc())).asNativeBuffer();
        }
        if (body instanceof byte[] bytes) {
            // never written to: an array shared by the responses is not copied
            return Unpooled.wrappedBuffer(bytes);
        }
        if (body instanceof java.nio.ByteBuffer buffer) {
            return Unpooled.wrappedBuffer(buffer.duplicate());
        }
        if (body instanceof ByteBuf buf) {
            return buf;
        }
        if (body instanceof io.micronaut.core.io.buffer.ByteBuffer<?> buffer) {
            if (buffer.asNativeBuffer() instanceof ByteBuf buf) {
                return buf;
            }
            return Unpooled.wrappedBuffer(buffer.toByteArray());
        }
        CharSequence text = (CharSequence) body;
        Charset charset = response.getContentType().flatMap(MediaType::getCharset).orElse(StandardCharsets.UTF_8);
        return ByteBufUtil.encodeString(ctx.alloc(), CharBuffer.wrap(text), charset);
    }

    /**
     * Handles a request as an ordinary request, e.g. {@link RoutingInBoundHandler}, when an
     * asynchronous direct route declined it.
     */
    @FunctionalInterface
    interface OrdinaryRequests {
        /**
         * @param ctx            The context of the channel
         * @param request        The request
         * @param body           Its body, untouched
         * @param outboundAccess Writes its response
         */
        void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess);
    }

    /**
     * A request target or a query that is not a valid URI, e.g. with a malformed escape, found
     * when the lookup reads it: the request is not a direct route's, and the ordinary path
     * answers it with {@code 400}.
     */
    private static final class InvalidRequestException extends RuntimeException {
        InvalidRequestException(IllegalArgumentException cause) {
            super(cause.getMessage(), cause, false, false);
        }
    }

    /**
     * A response ready to be written.
     *
     * @param head    Its status and headers
     * @param content Its body
     */
    private record Prepared(HttpResponse head, ByteBuf content) {
    }

    /**
     * @param type      The type of the body
     * @param mediaType The media type of the response
     */
    private record WriterKey(Class<?> type, MediaType mediaType) {
    }

    /**
     * @param type      The type of the body
     * @param mediaType The media type of the response
     * @param writer    The writer of the type
     * @param blocking  Whether the writer blocks
     */
    private record ResolvedWriter(Argument<Object> type, MediaType mediaType, MessageBodyWriter<Object> writer, boolean blocking) {
    }

    /**
     * The Netty request as the direct routes read it. The request target is validated when the
     * lookup first reads the path, like the {@link NettyHttpRequest} the server would create
     * validates it, so a request whose method has no direct route is not scanned: an invalid
     * target throws an {@link InvalidRequestException}. The query is decoded when a query
     * condition first reads it, like {@link NettyHttpRequest} decodes it: with the charset of the
     * content type of the request, or the default charset of the server, and the limits of the
     * server.
     */
    private static final class NettyDirectRequest implements DirectRequest {
        private final ChannelHandlerContext ctx;
        private final HttpRequest request;
        private final NettyHttpServerConfiguration configuration;
        private @Nullable String target;
        private @Nullable Map<String, List<String>> query;

        NettyDirectRequest(ChannelHandlerContext ctx, HttpRequest request, NettyHttpServerConfiguration configuration) {
            this.ctx = ctx;
            this.request = request;
            this.configuration = configuration;
        }

        /**
         * @return The request target, validated like the request the server would create, e.g.
         * an absolute-form or escaped target
         * @throws InvalidRequestException if it is not a valid URI
         */
        private String target() {
            String validated = target;
            if (validated == null) {
                validated = request.uri();
                if (!UriUtil.isValidPath(validated)) {
                    try {
                        validated = AbstractNettyHttpRequest.validatedTarget(validated, configuration.isEscapeHtmlUrl());
                    } catch (IllegalArgumentException e) {
                        throw new InvalidRequestException(e);
                    }
                }
                target = validated;
            }
            return validated;
        }

        @Override
        public List<String> queryParameters(String name) {
            Map<String, List<String>> parameters = query;
            if (parameters == null) {
                Charset charset = HttpUtil.getCharset(request, configuration.getDefaultCharset());
                try {
                    parameters = new QueryStringDecoder(target(), charset, true, configuration.getMaxParams(),
                        configuration.isSemicolonIsNormalChar()).parameters();
                } catch (IllegalArgumentException e) {
                    throw new InvalidRequestException(e);
                }
                query = parameters;
            }
            return parameters.getOrDefault(name, List.of());
        }

        @Override
        public String methodName() {
            return request.method().name();
        }

        @Override
        public String path() {
            // as NettyHttpRequest#getPath
            return AbstractNettyHttpRequest.parsePath(target());
        }

        @Override
        public List<String> headers(String name) {
            return request.headers().getAll(name);
        }

        @Override
        public @Nullable InetSocketAddress peerAddress() {
            SocketAddress address = ctx.channel().remoteAddress();
            return address instanceof InetSocketAddress inet ? inet : null;
        }
    }
}
