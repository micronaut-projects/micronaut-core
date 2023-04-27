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
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.MediaTypeProvider;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.body.NettyWriteClosure;
import io.micronaut.http.netty.body.NettyWriteContext;
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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.ClosedChannelException;
import java.util.Collections;
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
    private final MessageBodyHandlerRegistry messageBodyHandlerRegistry;
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
        this.messageBodyHandlerRegistry = embeddedServerContext.getMessageBodyHandlerRegistry();
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
            outboundAccess.attachment(errorRequest);
            new NettyRequestLifecycle(this, outboundAccess, errorRequest).handleException(e.getCause() == null ? e : e.getCause());
            if (request instanceof StreamedHttpRequest streamed) {
                streamed.closeIfNoSubscriber();
            } else {
                ((FullHttpRequest) request).release();
            }
            return;
        }
        outboundAccess.attachment(mnRequest);
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
        if (nettyRequest.getMethod() != HttpMethod.HEAD && body != null) {
            @SuppressWarnings("unchecked") final RouteInfo<Object> routeInfo = response.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class).orElse(null);

            if (Publishers.isConvertibleToPublisher(body)) {
                response.body(null);
                DelegateStreamedHttpResponse streamedResponse = new DelegateStreamedHttpResponse(
                    toNettyResponse(response),
                    mapToHttpContent(nettyRequest, response, body, routeInfo, nettyRequest.getChannelHandlerContext())
                );
                outboundAccess.writeStreamed(streamedResponse);
                return;
            }

            MessageBodyWriter<Object> messageBodyWriter = (MessageBodyWriter<Object>) response.getBodyWriter().orElse(null);
            MediaType responseMediaType = response.getContentType().orElse(null);
            Argument<Object> responseBodyType;
            if (routeInfo != null && response.getAttribute(HttpAttributes.ERROR).isEmpty()) {
                // todo: resolve error writer from exception handler
                responseBodyType = (Argument<Object>) routeInfo.getResponseBodyType();
            } else {
                responseBodyType = Argument.of((Class<Object>) body.getClass());
            }
            if (responseMediaType == null) {
                if (body instanceof MediaTypeProvider mediaTypeProvider) {
                    responseMediaType = mediaTypeProvider.getMediaType();
                } else if (routeInfo != null) {
                    responseMediaType = routeExecutor.resolveDefaultResponseContentType(nettyRequest, routeInfo);
                } else {
                    responseMediaType = MediaType.APPLICATION_JSON_TYPE;
                }
            }

            if (messageBodyWriter == null) {
                // lookup write to use, any logic that hits this path should consider setting
                // a body writer on the response before writing
                messageBodyWriter = this.messageBodyHandlerRegistry
                    .findWriter(responseBodyType, Collections.singletonList(responseMediaType)).orElse(null);
            }

            if (messageBodyWriter != null && responseBodyType.isInstance(body) && messageBodyWriter.isWriteable(responseBodyType, responseMediaType)) {
                NettyWriteClosure<Object> closure = wrap(messageBodyWriter.prepare(responseBodyType, responseMediaType));
                handleMissingConnectionHeader(response, nettyRequest, outboundAccess);
                if (closure.isBlocking()) {
                    getIoExecutor().execute(() -> writeNettyMessageBody(nettyRequest.getChannelHandlerContext(), nettyRequest, (MutableHttpResponse<Object>) response, body, closure, outboundAccess));
                } else {
                    writeNettyMessageBody(nettyRequest.getChannelHandlerContext(), nettyRequest, (MutableHttpResponse<Object>) response, body, closure, outboundAccess);
                }
            } else {
                encodeResponseBody(
                    nettyRequest.getChannelHandlerContext(),
                    nettyRequest,
                    response,
                    routeInfo,
                    body,
                    responseMediaType
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
        NettyWriteClosure<Object> nettyMessageBodyWriter,
        PipeliningServerHandler.OutboundAccess outboundAccess) {
        try {
            nettyMessageBodyWriter.writeTo(
                nettyRequest,
                response,
                body,
                outboundAccess
            );
        } catch (CodecException e) {
            final MutableHttpResponse<?> errorResponse = routeExecutor.createDefaultErrorResponse(nettyRequest, e);
            encodeResponseBody(context, nettyRequest, errorResponse, null, errorResponse.body(), errorResponse.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE));
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
                    ByteBuffer<?> byteBuffer = messageBodyWriter.prepare(responseBodyType, finalMediaType).writeTo(
                        message,
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
            .contextWrite(reactorContext -> reactorContext.put(ServerRequestContext.KEY, request));

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
        Object body,
        @NonNull MediaType mediaType) {
        if (body == null) {
            return;
        } else {
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
        }
        if (nettyResponse instanceof StreamedHttpResponse streamed) {
            nettyResponse.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            outboundAccess.writeStreamed(streamed);
        } else {
            FullHttpResponse fullResponse = (FullHttpResponse) nettyResponse;
            if (PipeliningServerHandler.canHaveBody(fullResponse.status()) && request.getMethod() != HttpMethod.HEAD) {
                nettyResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, fullResponse.content().readableBytes());
            }
            outboundAccess.writeFull(fullResponse);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Response {} - {} {}",
                nettyResponse.status().code(),
                ((HttpRequest<?>) request).getMethodName(),
                ((HttpRequest<?>) request).getUri());
        }
    }

    private void handleMissingConnectionHeader(MutableHttpResponse<?> message, HttpRequest<?> request, PipeliningServerHandler.OutboundAccess outboundAccess) {
        boolean decodeError = request instanceof NettyHttpRequest<?> nettyRequest &&
            nettyRequest.getNativeRequest().decoderResult().isFailure();

        if (decodeError || (message.code() >= 500 && !serverConfiguration.isKeepAliveOnServerError())) {
            outboundAccess.closeAfterWrite();
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

    <T> NettyWriteClosure<T> wrap(MessageBodyWriter.WriteClosure<T> closure) {
        if (closure instanceof NettyWriteClosure<T> nettyClosure) {
            return nettyClosure;
        } else {
            return new CompatNettyWriteClosure<>(closure);
        }
    }

    private final class CompatNettyWriteClosure<T> extends NettyWriteClosure<T> {
        private final MessageBodyWriter.WriteClosure<T> delegate;

        CompatNettyWriteClosure(MessageBodyWriter.WriteClosure<T> delegate) {
            super(delegate.isBlocking());
            this.delegate = delegate;
        }

        @Override
        public void writeTo(HttpRequest<?> request, MutableHttpResponse<T> outgoingResponse, T object, NettyWriteContext nettyContext) throws CodecException {
            NettyByteBufferFactory bufferFactory = new NettyByteBufferFactory(nettyContext.alloc());
            ByteBuffer<?> byteBuffer = delegate.writeTo(
                object,
                outgoingResponse.getHeaders(),
                bufferFactory
            );
            setResponseBody(outgoingResponse, (ByteBuf) byteBuffer.asNativeBuffer());
            writeFinalNettyResponse(outgoingResponse, (NettyHttpRequest<?>) request, (PipeliningServerHandler.OutboundAccess) nettyContext);
        }

        @Override
        public void writeTo(T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            delegate.writeTo(object, outgoingHeaders, outputStream);
        }

        @Override
        public ByteBuffer<?> writeTo(T object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
            return delegate.writeTo(object, outgoingHeaders, bufferFactory);
        }
    }
}
