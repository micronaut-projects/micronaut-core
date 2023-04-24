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
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.body.NettyMessageBodyWriter;
import io.micronaut.http.netty.stream.JsonSubscriber;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.handler.PipeliningServerHandler;
import io.micronaut.http.server.netty.handler.RequestHandler;
import io.micronaut.runtime.http.codec.TextPlainCodec;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
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
public final class RoutingInBoundHandler implements RequestHandler {

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
    final Supplier<ExecutorService> ioExecutorSupplier;
    final boolean multipartEnabled;
    ExecutorService ioExecutor;
    final ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher;
    final RouteExecutor routeExecutor;
    final ConversionService conversionService;

    /**
     * @param serverConfiguration          The Netty HTTP server configuration
     * @param embeddedServerContext        The embedded server context
     * @param ioExecutor                   The IO executor
     * @param httpContentProcessorResolver The http content processor resolver
     * @param terminateEventPublisher      The terminate event publisher
     * @param conversionService            The conversion service
     */
    RoutingInBoundHandler(
        NettyHttpServerConfiguration serverConfiguration,
        NettyEmbeddedServices embeddedServerContext,
        Supplier<ExecutorService> ioExecutor,
        HttpContentProcessorResolver httpContentProcessorResolver,
        ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher,
        ConversionService conversionService) {
        this.mediaTypeCodecRegistry = embeddedServerContext.getMediaTypeCodecRegistry();
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

    private void cleanupRequest(NettyHttpRequest<?> request) {
        try {
            request.release();
        } finally {
            if (!terminateEventPublisher.isEmpty()) {
                try {
                    terminateEventPublisher.publishEvent(new HttpRequestTerminatedEvent(request));
                } catch (Exception e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error publishing request terminated event: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    @Override
    public void responseWritten(Object attachment) {
        if (attachment != null) {
            cleanupRequest((NettyHttpRequest<?>) attachment);
        }
    }

    @Override
    public void handleUnboundError(Throwable cause) {
        // short-circuit ignorable exceptions: This is also handled by RouteExecutor, but handling this early avoids
        // running any filters
        if (isIgnorable(cause)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Swallowed an IOException caused by client connectivity: " + cause.getMessage(), cause);
            }
            return;
        }

        if (cause instanceof SSLException || cause.getCause() instanceof SSLException) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Micronaut Server Error - No request state present. Cause: " + cause.getMessage(), cause);
            }
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Micronaut Server Error - No request state present. Cause: " + cause.getMessage(), cause);
            }
        }
    }

    @Override
    public void accept(ChannelHandlerContext ctx, io.netty.handler.codec.http.HttpRequest request, PipeliningServerHandler.OutboundAccess outboundAccess) {
        NettyHttpRequest<Object> mnRequest;
        try {
            mnRequest = new NettyHttpRequest<>(request, ctx, conversionService, serverConfiguration);
        } catch (IllegalArgumentException e) {
            // invalid URI
            NettyHttpRequest<Object> errorRequest = new NettyHttpRequest<>(
                new DefaultFullHttpRequest(request.protocolVersion(), request.method(), "/", Unpooled.EMPTY_BUFFER),
                ctx,
                conversionService,
                serverConfiguration
            );
            new NettyRequestLifecycle(this, outboundAccess, errorRequest).handleException(e.getCause() == null ? e : e.getCause());
            if (request instanceof StreamedHttpRequest streamed) {
                streamed.closeIfNoSubscriber();
            } else {
                ((FullHttpRequest) request).release();
            }
            return;
        }
        new NettyRequestLifecycle(this, outboundAccess, mnRequest).handleNormal();
    }

    public void writeResponse(PipeliningServerHandler.OutboundAccess outboundAccess,
                       NettyHttpRequest<?> nettyHttpRequest,
                       MutableHttpResponse<?> response,
                       Throwable throwable) {
        if (throwable != null) {
            response = routeExecutor.createDefaultErrorResponse(nettyHttpRequest, throwable);
        }
        if (response != null) {
            try {
                encodeHttpResponse(
                    outboundAccess,
                    nettyHttpRequest,
                    response,
                    response.body()
                );
            } catch (Throwable e) {
                response = routeExecutor.createDefaultErrorResponse(nettyHttpRequest, e);
                encodeHttpResponse(
                    outboundAccess,
                    nettyHttpRequest,
                    response,
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

    @SuppressWarnings("unchecked")
    private void encodeHttpResponse(
        PipeliningServerHandler.OutboundAccess outboundAccess,
        NettyHttpRequest<?> nettyRequest,
        MutableHttpResponse<?> response,
        Object body) {
        if (nettyRequest.getMethod() != HttpMethod.HEAD) {
            @SuppressWarnings("unchecked") final RouteInfo<Object> routeInfo = response.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class).orElse(null);
            MessageBodyWriter<Object> messageBodyWriter = (MessageBodyWriter<Object>) response.getBodyWriter().orElse(null);
            MediaType responseMediaType = response.getContentType().orElse(null);
            Argument<Object> responseBodyType = routeInfo != null ? (Argument<Object>) routeInfo.getResponseBodyType() : Argument.of((Class<Object>) body.getClass());
            if (responseMediaType == null) {
                if (routeInfo != null) {
                    responseMediaType = routeExecutor.resolveDefaultResponseContentType(nettyRequest, routeInfo);
                } else {
                    responseMediaType = MediaType.APPLICATION_JSON_TYPE;
                }
            }

            MediaType finalResponseMediaType = responseMediaType;
            if (messageBodyWriter != null && responseBodyType.isInstance(body) && messageBodyWriter.isWriteable(responseBodyType, responseMediaType)) {
                if (messageBodyWriter instanceof NettyMessageBodyWriter<Object> nettyMessageBodyWriter) {
                    handleMissingConnectionHeader(response, nettyRequest, outboundAccess);
                    if (nettyMessageBodyWriter.useIoExecutor()) {
                        getIoExecutor().execute(() -> writeNettyMessageBody(nettyRequest.getChannelHandlerContext(), nettyRequest, (MutableHttpResponse<Object>) response, body, responseBodyType, finalResponseMediaType, nettyMessageBodyWriter, outboundAccess));
                    } else {
                        writeNettyMessageBody(nettyRequest.getChannelHandlerContext(), nettyRequest, (MutableHttpResponse<Object>) response, body, responseBodyType, finalResponseMediaType, nettyMessageBodyWriter, outboundAccess);
                    }
                } else {
                    NettyByteBufferFactory bufferFactory = new NettyByteBufferFactory(nettyRequest.getChannelHandlerContext().alloc());
                    ByteBuffer<?> byteBuffer = messageBodyWriter.writeTo(
                        responseBodyType,
                        body,
                        responseMediaType,
                        response.getHeaders(),
                        bufferFactory
                    );
                    setResponseBody(response, (ByteBuf) byteBuffer.asNativeBuffer());
                    writeFinalNettyResponse(response, nettyRequest, outboundAccess);
                }
            } else if (body instanceof Publisher) {
                response.body(null);
                if (serverConfiguration.getServerType() == NettyHttpServerConfiguration.HttpServerType.FULL_CONTENT) {
                    // HttpStreamsHandler is not present, so we can't write a StreamedHttpResponse.
                    Flux.from(mapToHttpContent(nettyRequest, response, body, routeInfo, nettyRequest.getChannelHandlerContext())).collectList().subscribe(contents -> {
                        if (contents.size() == 0) {
                            setResponseBody(response, Unpooled.EMPTY_BUFFER);
                        } else if (contents.size() == 1) {
                            setResponseBody(response, contents.get(0).content().retain());
                        } else {
                            CompositeByteBuf composite = nettyRequest.getChannelHandlerContext().alloc().compositeBuffer();
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
                            outboundAccess
                        );
                    }, error -> {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error occurred writing publisher response: " + error.getMessage(), error);
                        }
                        HttpResponseStatus responseStatus;
                        if (error instanceof HttpStatusException statusException) {
                            responseStatus = HttpResponseStatus.valueOf(statusException.getStatus().getCode(), error.getMessage());
                        } else {
                            responseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                        }
                        outboundAccess.closeAfterWrite();
                        outboundAccess.writeFull(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, responseStatus));
                    });
                } else {
                    DelegateStreamedHttpResponse streamedResponse = new DelegateStreamedHttpResponse(
                        toNettyResponse(response),
                        mapToHttpContent(nettyRequest, response, body, routeInfo, nettyRequest.getChannelHandlerContext())
                    );
                    outboundAccess.attachment(nettyRequest);
                    outboundAccess.writeStreamed(streamedResponse);
                }
            } else {
                encodeResponseBody(
                    nettyRequest.getChannelHandlerContext(),
                    nettyRequest,
                    response,
                    routeInfo,
                    body
                );

                writeFinalNettyResponse(
                    response,
                    nettyRequest,
                    outboundAccess
                );
            }
        } else {
            response.body(null);
            writeFinalNettyResponse(
                response,
                nettyRequest,
                outboundAccess
            );
        }
    }

    private void writeNettyMessageBody(
        ChannelHandlerContext context,
        NettyHttpRequest<?> nettyRequest,
        MutableHttpResponse<Object> response,
        Object body,
        Argument<Object> responseBodyType,
        MediaType finalResponseMediaType,
        NettyMessageBodyWriter<Object> nettyMessageBodyWriter, PipeliningServerHandler.OutboundAccess outboundAccess) {
        try {
            nettyMessageBodyWriter.writeTo(
                nettyRequest,
                response,
                responseBodyType,
                body,
                finalResponseMediaType,
                outboundAccess
            );
        } catch (CodecException e) {
            final MutableHttpResponse<?> errorResponse = routeExecutor.createDefaultErrorResponse(nettyRequest, e);
            encodeResponseBody(context, nettyRequest, errorResponse, null, errorResponse.body());
            writeFinalNettyResponse(
                errorResponse,
                nettyRequest,
                outboundAccess
            );
        }
    }

    private Flux<HttpContent> mapToHttpContent(NettyHttpRequest<?> request,
                                               MutableHttpResponse<?> response,
                                               Object body,
                                               RouteInfo<Object> routeInfo,
                                               ChannelHandlerContext context) {
        MediaType mediaType = response.getContentType().orElse(null);
        NettyByteBufferFactory byteBufferFactory = new NettyByteBufferFactory(context.alloc());
        Flux<Object> bodyPublisher = Flux.from(Publishers.convertPublisher(conversionService, body, Publisher.class));
        Flux<HttpContent> httpContentPublisher;
        boolean isJson = false;
        if (routeInfo != null) {
            if (mediaType == null) {
                mediaType = routeExecutor.resolveDefaultResponseContentType(request, routeInfo);
            }
            isJson = mediaType != null &&
                mediaType.getExtension().equals(MediaType.EXTENSION_JSON) && routeInfo.isResponseBodyJsonFormattable();
            MediaType finalMediaType = mediaType;
            @SuppressWarnings("unchecked") Argument<Object> responseBodyType = (Argument<Object>) routeInfo.getResponseBodyType();
            httpContentPublisher = bodyPublisher.map(message -> {
                MessageBodyWriter<Object> messageBodyWriter = routeInfo.getMessageBodyWriter();
                if (messageBodyWriter != null && responseBodyType.isInstance(message) && messageBodyWriter.isWriteable(responseBodyType, finalMediaType)) {
                    ByteBuffer<?> byteBuffer = messageBodyWriter.writeTo(
                        responseBodyType,
                        message,
                        finalMediaType,
                        response.getHeaders(),
                        byteBufferFactory
                    );
                    return new DefaultHttpContent((ByteBuf) byteBuffer.asNativeBuffer());
                } else {
                    return handleArbitraryHttpContent(byteBufferFactory, finalMediaType, message, responseBodyType);
                }
            });
        } else {
            MediaType finalMediaType = mediaType;
            httpContentPublisher = bodyPublisher.map(message -> handleArbitraryHttpContent(byteBufferFactory, finalMediaType, message, null));
        }

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
            .doAfterTerminate(() -> cleanupRequest(request));

        return httpContentPublisher;
    }

    @NonNull
    private HttpContent handleArbitraryHttpContent(
        NettyByteBufferFactory byteBufferFactory,
        MediaType mediaType,
        Object message,
        @Nullable Argument<Object> responseBodyType) {
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
            MediaTypeCodec codec = mediaTypeCodecRegistry.findCodec(mediaType, message.getClass()).orElse(
                new TextPlainCodec(serverConfiguration.getDefaultCharset(), conversionService));

            if (LOG.isTraceEnabled()) {
                LOG.trace("Encoding emitted response object [{}] using codec: {}", message, codec);
            }
            ByteBuffer<ByteBuf> encoded;
            if (responseBodyType != null) {
                if (responseBodyType.isInstance(message)) {
                    encoded = codec.encode(responseBodyType, message, byteBufferFactory);
                } else {
                    encoded = codec.encode(message, byteBufferFactory);
                }
            } else {
                encoded = codec.encode(message, byteBufferFactory);
            }
            httpContent = new DefaultHttpContent(encoded.asNativeBuffer());
        }
        return httpContent;
    }

    private void encodeResponseBody(
        ChannelHandlerContext context,
        HttpRequest<?> request,
        MutableHttpResponse<?> message,
        @Nullable RouteInfo<Object> routeInfo,
        Object body) {
        if (body == null) {
            return;
        }

        MediaType mediaType = message.getContentType().orElse(null);
        if (mediaType == null) {
            mediaType = routeInfo != null ? routeExecutor.resolveDefaultResponseContentType(request, routeInfo) : MediaType.APPLICATION_JSON_TYPE;
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
            Argument<Object> bodyType = routeInfo != null ? (Argument<Object>) routeInfo.getResponseBodyType() : null;
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

    private void writeFinalNettyResponse(MutableHttpResponse<?> message, NettyHttpRequest<?> request, PipeliningServerHandler.OutboundAccess outboundAccess) {
        // default Connection header if not set explicitly
        handleMissingConnectionHeader(message, request, outboundAccess);
        io.netty.handler.codec.http.HttpResponse nettyResponse = NettyHttpResponseBuilder.toHttpResponse(message);
        // close handled by HttpServerKeepAliveHandler
        if (request.getNativeRequest() instanceof StreamedHttpRequest streamed && !streamed.isConsumed()) {
            // consume incoming data
            Flux.from(streamed).subscribe(HttpContent::release);
        } else {
            syncWriteAndFlushNettyResponse(outboundAccess, request, nettyResponse);
        }
    }

    @NotNull
    private GenericFutureListener<Future<? super Void>> newRequestCompleter(HttpRequest<?> request, ChannelHandlerContext context) {
        return future -> {
            try {
                if (!future.isSuccess()) {
                    final Throwable throwable = future.cause();
                    if (!isIgnorable(throwable)) {
                        if (throwable instanceof Http2Exception.StreamException se && se.error() == Http2Error.STREAM_CLOSED) {
                            // ignore
                            return;
                        }
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error writing final response: " + throwable.getMessage(), throwable);
                        }
                    }
                }
            } finally {
                if (request instanceof NettyHttpRequest) {
                    cleanupRequest((NettyHttpRequest<?>) request);
                }
                context.read();
            }
        };
    }

    private void handleMissingConnectionHeader(MutableHttpResponse<?> message, HttpRequest<?> request, PipeliningServerHandler.OutboundAccess outboundAccess) {
        int httpStatus = message.code();

        final io.micronaut.http.HttpVersion httpVersion = request.getHttpVersion();
        final boolean isHttp2 = httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0;

        boolean decodeError = request instanceof NettyHttpRequest &&
            ((NettyHttpRequest<?>) request).getNativeRequest().decoderResult().isFailure();

        if (!isHttp2 && !message.getHeaders().contains(HttpHeaders.CONNECTION)) {
            if (!decodeError && (httpStatus < 500 || serverConfiguration.isKeepAliveOnServerError())) {
                message.getHeaders().set(HttpHeaders.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                message.getHeaders().set(HttpHeaders.CONNECTION, HttpHeaderValues.CLOSE);
            }
        }
    }

    private void syncWriteAndFlushNettyResponse(
        PipeliningServerHandler.OutboundAccess outboundAccess,
        HttpRequest<?> request,
        io.netty.handler.codec.http.HttpResponse nettyResponse
    ) {
        if (nettyResponse instanceof StreamedHttpResponse streamed) {
            outboundAccess.attachment(request instanceof NettyHttpRequest<?> ? request : null);
            outboundAccess.writeStreamed(streamed);
        } else {
            if (request instanceof NettyHttpRequest<?> nettyRequest) {
                // no need to wait, can release immediately
                cleanupRequest(nettyRequest);
                outboundAccess.attachment(null);
            }
            outboundAccess.writeFull((FullHttpResponse) nettyResponse);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Response {} - {} {}",
                nettyResponse.status().code(),
                request.getMethodName(),
                request.getUri());
        }
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
        } else if (body instanceof ByteBuffer<?> byteBuffer) {
            Object nativeBuffer = byteBuffer.asNativeBuffer();
            if (nativeBuffer instanceof ByteBuf bb) {
                byteBuf = bb;
            } else {
                byteBuf = Unpooled.wrappedBuffer(byteBuffer.asNioBuffer());
            }
        } else if (body instanceof byte[] bytes) {
            byteBuf = Unpooled.wrappedBuffer(bytes);

        } else if (body instanceof Writable writable) {
            byteBuf = context.alloc().ioBuffer(128);
            ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
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
            if (wrapped instanceof ReferenceCounted referenceCounted) {
                referenceCounted.release();
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

}
