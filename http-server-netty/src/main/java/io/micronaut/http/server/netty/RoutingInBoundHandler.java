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
package io.micronaut.http.server.netty;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.stream.JsonSubscriber;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandler;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandlerRegistry;
import io.micronaut.runtime.http.codec.TextPlainCodec;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Internal implementation of the {@link io.netty.channel.ChannelInboundHandler} for Micronaut.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@Sharable
@SuppressWarnings("FileLength")
final class RoutingInBoundHandler extends SimpleChannelInboundHandler<io.micronaut.http.HttpRequest<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingInBoundHandler.class);
    /*
     * Also present in {@link RouteExecutor}.
     */
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
        "^.*(?:connection (?:reset|closed|abort|broken)|broken pipe).*$", Pattern.CASE_INSENSITIVE);
    final StaticResourceResolver staticResourceResolver;
    final NettyHttpServerConfiguration serverConfiguration;
    final HttpContentProcessorResolver httpContentProcessorResolver;
    final RequestArgumentSatisfier requestArgumentSatisfier;
    final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    final NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry;
    final Supplier<ExecutorService> ioExecutorSupplier;
    final boolean multipartEnabled;
    ExecutorService ioExecutor;
    final ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher;
    final RouteExecutor routeExecutor;
    final ConversionService conversionService;

    /**
     * @param customizableResponseTypeHandlerRegistry The customizable response type handler registry
     * @param serverConfiguration                     The Netty HTTP server configuration
     * @param embeddedServerContext                   The embedded server context
     * @param ioExecutor                              The IO executor
     * @param httpContentProcessorResolver            The http content processor resolver
     * @param terminateEventPublisher                 The terminate event publisher
     * @param conversionService                       The conversion service
     */
    RoutingInBoundHandler(
        NettyHttpServerConfiguration serverConfiguration,
        NettyCustomizableResponseTypeHandlerRegistry customizableResponseTypeHandlerRegistry,
        NettyEmbeddedServices embeddedServerContext,
        Supplier<ExecutorService> ioExecutor,
        HttpContentProcessorResolver httpContentProcessorResolver,
        ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher,
        ConversionService conversionService) {
        this.mediaTypeCodecRegistry = embeddedServerContext.getMediaTypeCodecRegistry();
        this.customizableResponseTypeHandlerRegistry = customizableResponseTypeHandlerRegistry;
        this.staticResourceResolver = embeddedServerContext.getStaticResourceResolver();
        this.ioExecutorSupplier = ioExecutor;
        this.requestArgumentSatisfier = embeddedServerContext.getRequestArgumentSatisfier();
        this.serverConfiguration = serverConfiguration;
        this.httpContentProcessorResolver = httpContentProcessorResolver;
        this.terminateEventPublisher = terminateEventPublisher;
        Optional<Boolean> isMultiPartEnabled = serverConfiguration.getMultipart().getEnabled();
        this.multipartEnabled = isMultiPartEnabled.isEmpty() || isMultiPartEnabled.get();
        this.routeExecutor = embeddedServerContext.getRouteExecutor();
        this.conversionService = conversionService;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        cleanupIfNecessary(ctx);
    }

    @Override
    public void channelInactive(@NonNull ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (ctx.channel().isWritable()) {
            ctx.flush();
        }
        cleanupIfNecessary(ctx);
    }

    private void cleanupIfNecessary(ChannelHandlerContext ctx) {
        NettyHttpRequest.remove(ctx);
    }

    private void cleanupRequest(ChannelHandlerContext ctx, NettyHttpRequest<?> request) {
        try {
            request.release();
        } finally {
            if (terminateEventPublisher != ApplicationEventPublisher.NO_OP) {
                ctx.executor().execute(() -> {
                    try {
                        terminateEventPublisher.publishEvent(new HttpRequestTerminatedEvent(request));
                    } catch (Exception e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error publishing request terminated event: " + e.getMessage(), e);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        try {
            if (evt instanceof IdleStateEvent idleStateEvent) {
                IdleState state = idleStateEvent.state();
                if (state == IdleState.ALL_IDLE) {
                    ctx.close();
                }
            }
        } finally {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // short-circuit ignorable exceptions: This is also handled by RouteExecutor, but handling this early avoids
        // running any filters
        if (isIgnorable(cause)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Swallowed an IOException caused by client connectivity: " + cause.getMessage(), cause);
            }
            return;
        }

        NettyHttpRequest<?> nettyHttpRequest = NettyHttpRequest.remove(ctx);
        if (nettyHttpRequest == null) {
            if (cause instanceof SSLException || cause.getCause() instanceof SSLException) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Micronaut Server Error - No request state present. Cause: " + cause.getMessage(), cause);
                }
            } else {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Micronaut Server Error - No request state present. Cause: " + cause.getMessage(), cause);
                }
            }

            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
            return;
        }
        new NettyRequestLifecycle(this, ctx, nettyHttpRequest).handleException(cause);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, io.micronaut.http.HttpRequest<?> httpRequest) {
        new NettyRequestLifecycle(this, ctx, (NettyHttpRequest<?>) httpRequest).handleNormal();
    }

    void writeResponse(ChannelHandlerContext ctx,
                               NettyHttpRequest<?> nettyHttpRequest,
                               MutableHttpResponse<?> response,
                               Throwable throwable) {
        if (throwable != null) {
            response = routeExecutor.createDefaultErrorResponse(nettyHttpRequest, throwable);
        }
        if (response == null) {
            ctx.read();
        } else {
            try {
                encodeHttpResponse(
                    ctx,
                    nettyHttpRequest,
                    response,
                    null,
                    response.body()
                );
            } catch (Throwable e) {
                response = routeExecutor.createDefaultErrorResponse(nettyHttpRequest, e);
                encodeHttpResponse(
                    ctx,
                    nettyHttpRequest,
                    response,
                    null,
                    response.body()
                );
            }
        }
    }

    ExecutorService getIoExecutor() {
        ExecutorService executor = this.ioExecutor;
        if (executor == null) {
            synchronized (this) { // double check
                executor = this.ioExecutor;
                if (executor == null) {
                    executor = this.ioExecutorSupplier.get();
                    this.ioExecutor = executor;
                }
            }
        }
        return executor;
    }

    private void encodeHttpResponse(
        ChannelHandlerContext context,
        NettyHttpRequest<?> nettyRequest,
        MutableHttpResponse<?> response,
        @Nullable Argument<Object> bodyType,
        Object body) {
        boolean isNotHead = nettyRequest.getMethod() != HttpMethod.HEAD;

        if (isNotHead) {
            if (body instanceof Writable) {
                getIoExecutor().execute(() -> {
                    ByteBuf byteBuf = context.alloc().ioBuffer(128);
                    ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
                    try {
                        Writable writable = (Writable) body;
                        writable.writeTo(outputStream, nettyRequest.getCharacterEncoding());
                        response.body(byteBuf);
                        if (!response.getContentType().isPresent()) {
                            response.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class).ifPresent((routeInfo) ->
                                response.contentType(routeExecutor.resolveDefaultResponseContentType(nettyRequest, routeInfo)));
                        }
                        writeFinalNettyResponse(
                            response,
                            nettyRequest,
                            context
                        );
                    } catch (IOException e) {
                        final MutableHttpResponse<?> errorResponse = routeExecutor.createDefaultErrorResponse(nettyRequest, e);
                        writeFinalNettyResponse(
                            errorResponse,
                            nettyRequest,
                            context
                        );
                    }
                });
            } else if (body instanceof Publisher) {
                response.body(null);
                if (serverConfiguration.getServerType() == NettyHttpServerConfiguration.HttpServerType.FULL_CONTENT) {
                    // HttpStreamsHandler is not present, so we can't write a StreamedHttpResponse.
                    Flux.from(mapToHttpContent(nettyRequest, response, body, context)).collectList().subscribe(contents -> {
                        if (contents.size() == 0) {
                            setResponseBody(response, Unpooled.EMPTY_BUFFER);
                        } else if (contents.size() == 1) {
                            setResponseBody(response, contents.get(0).content().retain());
                        } else {
                            CompositeByteBuf composite = context.alloc().compositeBuffer();
                            for (HttpContent c : contents) {
                                composite.addComponent(true, c.content().retain());
                            }
                            setResponseBody(response, composite);
                        }
                        for (HttpContent content : contents) {
                            content.release();
                        }

                        writeFinalNettyResponse(
                            response,
                            nettyRequest,
                            context
                        );
                    }, error -> {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error occurred writing publisher response: " + error.getMessage(), error);
                        }
                        HttpResponseStatus responseStatus;
                        if (error instanceof HttpStatusException) {
                            responseStatus = HttpResponseStatus.valueOf(((HttpStatusException) error).getStatus().getCode(), error.getMessage());
                        } else {
                            responseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                        }
                        context.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, responseStatus))
                            .addListener(ChannelFutureListener.CLOSE);
                    });
                } else {
                    DelegateStreamedHttpResponse streamedResponse = new DelegateStreamedHttpResponse(
                        toNettyResponse(response),
                        mapToHttpContent(nettyRequest, response, body, context)
                    );
                    context.writeAndFlush(streamedResponse);
                    context.read();
                }
            } else {
                encodeResponseBody(
                    context,
                    nettyRequest,
                    response,
                    bodyType,
                    body
                );

                writeFinalNettyResponse(
                    response,
                    nettyRequest,
                    context
                );
            }
        } else {
            response.body(null);
            writeFinalNettyResponse(
                response,
                nettyRequest,
                context
            );
        }
    }

    private Flux<HttpContent> mapToHttpContent(NettyHttpRequest<?> request,
                                               MutableHttpResponse<?> response,
                                               Object body,
                                               ChannelHandlerContext context) {
        final RouteInfo<?> routeInfo = response.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class).orElse(null);
        final boolean hasRouteInfo = routeInfo != null;
        MediaType mediaType = response.getContentType().orElse(null);
        if (mediaType == null && hasRouteInfo) {
            mediaType = routeExecutor.resolveDefaultResponseContentType(request, routeInfo);
        }
        boolean isJson = mediaType != null && mediaType.getExtension().equals(MediaType.EXTENSION_JSON) &&
            isJsonFormattable(hasRouteInfo ? routeInfo.getBodyType() : null);
        NettyByteBufferFactory byteBufferFactory = new NettyByteBufferFactory(context.alloc());

        Flux<Object> bodyPublisher = Flux.from(Publishers.convertPublisher(conversionService, body, Publisher.class));

        MediaType finalMediaType = mediaType;
        Flux<HttpContent> httpContentPublisher = bodyPublisher.map(message -> {
            HttpContent httpContent;
            if (message instanceof ByteBuf bb) {
                httpContent = new DefaultHttpContent(bb);
            } else if (message instanceof ByteBuffer<?> byteBuffer) {
                Object nativeBuffer = byteBuffer.asNativeBuffer();
                if (nativeBuffer instanceof ByteBuf bb) {
                    httpContent = new DefaultHttpContent(bb);
                } else {
                    httpContent = new DefaultHttpContent(Unpooled.copiedBuffer(byteBuffer.asNioBuffer()));
                }
            } else if (message instanceof byte[] bytes) {
                httpContent = new DefaultHttpContent(Unpooled.copiedBuffer(bytes));
            } else if (message instanceof HttpContent hc) {
                httpContent = hc;
            } else {

                MediaTypeCodec codec = mediaTypeCodecRegistry.findCodec(finalMediaType, message.getClass()).orElse(
                        new TextPlainCodec(serverConfiguration.getDefaultCharset(), conversionService));

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Encoding emitted response object [{}] using codec: {}", message, codec);
                }
                ByteBuffer<ByteBuf> encoded;
                if (hasRouteInfo) {
                    //noinspection unchecked
                    final Argument<Object> bodyType = (Argument<Object>) routeInfo.getBodyType();
                    if (bodyType.isInstance(message)) {
                        encoded = codec.encode(bodyType, message, byteBufferFactory);
                    } else {
                        encoded = codec.encode(message, byteBufferFactory);
                    }
                } else {
                    encoded = codec.encode(message, byteBufferFactory);
                }
                httpContent = new DefaultHttpContent(encoded.asNativeBuffer());
            }
            return httpContent;
        });

        if (isJson) {
            // if the Publisher is returning JSON then in order for it to be valid JSON for each emitted element
            // we must wrap the JSON in array and delimit the emitted items

            httpContentPublisher = JsonSubscriber.lift(httpContentPublisher);
        }

        httpContentPublisher = httpContentPublisher
            .contextWrite(reactorContext -> reactorContext.put(ServerRequestContext.KEY, request))
            .doOnNext(httpContent ->
                // once an http content is written, read the next item if it is available
                context.read())
            .doAfterTerminate(() -> cleanupRequest(context, request));

        return httpContentPublisher;
    }

    private boolean isJsonFormattable(Argument<?> argument) {
        if (argument == null) {
            return false;
        }
        Class<?> javaType = argument.getType();
        if (Publishers.isConvertibleToPublisher(javaType)) {
            javaType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT).getType();
        }
        return !(javaType == byte[].class
            || ByteBuffer.class.isAssignableFrom(javaType)
            || ByteBuf.class.isAssignableFrom(javaType));
    }

    private void encodeResponseBody(
        ChannelHandlerContext context,
        HttpRequest<?> request,
        MutableHttpResponse<?> message,
        @Nullable Argument<Object> bodyType,
        Object body) {
        if (body == null) {
            return;
        }

        Optional<NettyCustomizableResponseTypeHandler> typeHandler = customizableResponseTypeHandlerRegistry
            .findTypeHandler(body.getClass());
        if (typeHandler.isPresent()) {
            NettyCustomizableResponseTypeHandler th = typeHandler.get();
            setBodyContent(message, new NettyCustomizableResponseTypeHandlerInvoker(th, body));
        } else {
            MediaType mediaType = message.getContentType().orElse(null);
            if (mediaType == null) {
                mediaType = message.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class)
                    .map(routeInfo -> routeExecutor.resolveDefaultResponseContentType(request, routeInfo))
                    // RouteExecutor will pick json by default, so we do too
                    .orElse(MediaType.APPLICATION_JSON_TYPE);
                message.contentType(mediaType);
            }
            if (body instanceof CharSequence) {
                ByteBuf byteBuf = Unpooled.wrappedBuffer(body.toString().getBytes(message.getCharacterEncoding()));
                setResponseBody(message, byteBuf);
            } else if (body instanceof byte[] bytes) {
                ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
                setResponseBody(message, byteBuf);
            } else if (body instanceof ByteBuffer<?> byteBuffer) {
                Object nativeBuffer = byteBuffer.asNativeBuffer();
                if (nativeBuffer instanceof ByteBuf bb) {
                    setResponseBody(message, bb);
                } else if (nativeBuffer instanceof java.nio.ByteBuffer nbb) {
                    ByteBuf byteBuf = Unpooled.wrappedBuffer(nbb);
                    setResponseBody(message, byteBuf);
                }
            } else if (body instanceof ByteBuf bb) {
                setResponseBody(message, bb);
            } else {
                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(mediaType, body.getClass());
                if (registeredCodec.isPresent()) {
                    MediaTypeCodec codec = registeredCodec.get();
                    encodeBodyWithCodec(message, bodyType, body, codec, context, request);
                } else {
                    MediaTypeCodec defaultCodec = new TextPlainCodec(serverConfiguration.getDefaultCharset(), conversionService);
                    encodeBodyWithCodec(message, bodyType, body, defaultCodec, context, request);
                }
            }
        }

    }

    private void writeFinalNettyResponse(MutableHttpResponse<?> message, HttpRequest<?> request, ChannelHandlerContext context) {
        int httpStatus = message.code();

        final io.micronaut.http.HttpVersion httpVersion = request.getHttpVersion();
        final boolean isHttp2 = httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0;

        boolean decodeError = request instanceof NettyHttpRequest &&
            ((NettyHttpRequest<?>) request).getNativeRequest().decoderResult().isFailure();

        GenericFutureListener<Future<? super Void>> requestCompletor = future -> {
            try {
                if (!future.isSuccess()) {
                    final Throwable throwable = future.cause();
                    if (!isIgnorable(throwable)) {
                        if (throwable instanceof Http2Exception.StreamException se) {
                            if (se.error() == Http2Error.STREAM_CLOSED) {
                                // ignore
                                return;
                            }
                        }
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error writing final response: " + throwable.getMessage(), throwable);
                        }
                    }
                }
            } finally {
                if (request instanceof NettyHttpRequest) {
                    cleanupRequest(context, (NettyHttpRequest<?>) request);
                }
                context.read();
            }
        };

        final Object body = message.body();
        if (body instanceof NettyCustomizableResponseTypeHandlerInvoker) {
            // default Connection header if not set explicitly
            if (!isHttp2) {
                if (!message.getHeaders().contains(HttpHeaders.CONNECTION)) {
                    if (!decodeError && (httpStatus < 500 || serverConfiguration.isKeepAliveOnServerError())) {
                        message.getHeaders().set(HttpHeaders.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    } else {
                        message.getHeaders().set(HttpHeaders.CONNECTION, HttpHeaderValues.CLOSE);
                    }
                }
            }
            NettyCustomizableResponseTypeHandlerInvoker handler = (NettyCustomizableResponseTypeHandlerInvoker) body;
            message.body(null);
            handler.invoke(request, message, context).addListener(requestCompletor);
        } else {
            io.netty.handler.codec.http.HttpResponse nettyResponse = NettyHttpResponseBuilder.toHttpResponse(message);
            io.netty.handler.codec.http.HttpHeaders nettyHeaders = nettyResponse.headers();

            // default Connection header if not set explicitly
            if (!isHttp2) {
                if (!nettyHeaders.contains(HttpHeaderNames.CONNECTION)) {
                    boolean expectKeepAlive = nettyResponse.protocolVersion().isKeepAliveDefault() || request.getHeaders().isKeepAlive();
                    if (!decodeError && expectKeepAlive && (httpStatus < 500 || serverConfiguration.isKeepAliveOnServerError())) {
                        nettyHeaders.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    } else {
                        nettyHeaders.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                    }
                }
            }

            // default to Transfer-Encoding: chunked if Content-Length not set or not already set
            if (!nettyHeaders.contains(HttpHeaderNames.CONTENT_LENGTH) && !nettyHeaders.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                nettyHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
            // close handled by HttpServerKeepAliveHandler
            final NettyHttpRequest<?> nettyHttpRequest = (NettyHttpRequest<?>) request;

            io.netty.handler.codec.http.HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();

            if (nativeRequest instanceof StreamedHttpRequest streamedHttpRequest && !streamedHttpRequest.isConsumed()) {
                // We have to clear the buffer of FlowControlHandler before writing the response
                // If this is a streamed request and there is still content to consume then subscribe
                // and write the buffer is empty.

                //noinspection ReactiveStreamsSubscriberImplementation
                streamedHttpRequest.subscribe(new Subscriber<HttpContent>() {
                    private Subscription streamSub;

                    @Override
                    public void onSubscribe(Subscription s) {
                        streamSub = s;
                        s.request(1);
                    }

                    @Override
                    public void onNext(HttpContent httpContent) {
                        httpContent.release();
                        streamSub.request(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        syncWriteAndFlushNettyResponse(context, request, nettyResponse, requestCompletor);
                    }

                    @Override
                    public void onComplete() {
                        syncWriteAndFlushNettyResponse(context, request, nettyResponse, requestCompletor);
                    }
                });
            } else {
                syncWriteAndFlushNettyResponse(context, request, nettyResponse, requestCompletor);
            }
        }
    }

    private void syncWriteAndFlushNettyResponse(
        ChannelHandlerContext context,
        HttpRequest<?> request,
        io.netty.handler.codec.http.HttpResponse nettyResponse,
        GenericFutureListener<Future<? super Void>> requestCompletor
    ) {
        context.write(nettyResponse).addListener(requestCompletor);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Response {} - {} {}",
                nettyResponse.status().code(),
                request.getMethodName(),
                request.getUri());
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @NonNull
    private io.netty.handler.codec.http.HttpResponse toNettyResponse(HttpResponse<?> message) {
        if (message instanceof NettyHttpResponseBuilder builder) {
            return builder.toHttpResponse();
        } else {
            return createNettyResponse(message).toHttpResponse();
        }
    }

    @NonNull
    private NettyMutableHttpResponse<?> createNettyResponse(HttpResponse<?> message) {
        Object body = message.body();
        io.netty.handler.codec.http.HttpHeaders nettyHeaders = new DefaultHttpHeaders(serverConfiguration.isValidateHeaders());
        message.getHeaders().forEach((BiConsumer<String, List<String>>) nettyHeaders::set);
        return new NettyMutableHttpResponse<>(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(message.code(), message.reason()),
                body instanceof ByteBuf ? body : null,
                conversionService
        );
    }

    private MutableHttpResponse<?> encodeBodyWithCodec(MutableHttpResponse<?> response,
                                                       @Nullable Argument<Object> bodyType,
                                                       Object body,
                                                       MediaTypeCodec codec,
                                                       ChannelHandlerContext context,
                                                       HttpRequest<?> request) {
        ByteBuf byteBuf;
        try {
            byteBuf = encodeBodyAsByteBuf(bodyType, body, codec, context, request);
            setResponseBody(response, byteBuf);
            return response;
        } catch (LinkageError e) {
            // rxjava swallows linkage errors for some reasons so if one occurs, rethrow as a internal error
            throw new InternalServerException("Fatal error encoding bytebuf: " + e.getMessage(), e);
        }
    }

    private void setResponseBody(MutableHttpResponse<?> response, ByteBuf byteBuf) {
        int len = byteBuf.readableBytes();
        MutableHttpHeaders headers = response.getHeaders();
        headers.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(len));

        setBodyContent(response, byteBuf);
    }

    private MutableHttpResponse<?> setBodyContent(MutableHttpResponse<?> response, Object bodyContent) {
        @SuppressWarnings("unchecked")
        MutableHttpResponse<?> res = response.body(bodyContent);
        return res;
    }

    private ByteBuf encodeBodyAsByteBuf(
        @Nullable Argument<Object> bodyType,
        Object body,
        MediaTypeCodec codec,
        ChannelHandlerContext context,
        HttpRequest<?> request) {
        ByteBuf byteBuf;
        if (body instanceof ByteBuf bb) {
            byteBuf = bb;
        } else if (body instanceof ByteBuffer) {
            ByteBuffer byteBuffer = (ByteBuffer) body;
            Object nativeBuffer = byteBuffer.asNativeBuffer();
            if (nativeBuffer instanceof ByteBuf) {
                byteBuf = (ByteBuf) nativeBuffer;
            } else {
                byteBuf = Unpooled.wrappedBuffer(byteBuffer.asNioBuffer());
            }
        } else if (body instanceof byte[] bytes) {
            byteBuf = Unpooled.wrappedBuffer(bytes);

        } else if (body instanceof Writable) {
            byteBuf = context.alloc().ioBuffer(128);
            ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
            Writable writable = (Writable) body;
            try {
                writable.writeTo(outputStream, request.getCharacterEncoding());
            } catch (IOException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error(e.getMessage());
                }
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Encoding emitted response object [{}] using codec: {}", body, codec);
            }
            ByteBuffer<ByteBuf> wrapped;
            if (bodyType != null && bodyType.isInstance(body)) {
                wrapped = codec.encode(bodyType, body, new NettyByteBufferFactory(context.alloc()));
            } else {
                wrapped = codec.encode(body, new NettyByteBufferFactory(context.alloc()));
            }
            // keep the ByteBuf, release the wrapper
            // this is probably a no-op, but it's the right thing to do anyway
            byteBuf = wrapped.asNativeBuffer().retain();
            if (wrapped instanceof ReferenceCounted) {
                ((ReferenceCounted) wrapped).release();
            }
        }
        return byteBuf;
    }

    /**
     * Is the exception ignorable by Micronaut.
     *
     * @param cause The cause
     * @return True if it can be ignored.
     */
    boolean isIgnorable(Throwable cause) {
        if (cause instanceof ClosedChannelException || cause.getCause() instanceof ClosedChannelException) {
            return true;
        }
        String message = cause.getMessage();
        return cause instanceof IOException && message != null && IGNORABLE_ERROR_MESSAGE.matcher(message).matches();
    }

    /**
     * Used as a handle to the {@link NettyCustomizableResponseTypeHandler}.
     */
    private static class NettyCustomizableResponseTypeHandlerInvoker {
        final NettyCustomizableResponseTypeHandler handler;
        final Object body;

        NettyCustomizableResponseTypeHandlerInvoker(NettyCustomizableResponseTypeHandler handler, Object body) {
            this.handler = handler;
            this.body = body;
        }

        @SuppressWarnings("unchecked")
        ChannelFuture invoke(HttpRequest<?> request, MutableHttpResponse response, ChannelHandlerContext channelHandlerContext) {
            return this.handler.handle(body, request, response, channelHandlerContext);
        }
    }
}
