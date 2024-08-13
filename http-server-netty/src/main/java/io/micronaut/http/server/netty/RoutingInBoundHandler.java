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
package io.micronaut.http.server.netty;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.DelayedSubscriber;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MediaTypeProvider;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.body.NettyBodyWriter;
import io.micronaut.http.netty.body.NettyJsonHandler;
import io.micronaut.http.netty.body.NettyWriteContext;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.stream.JsonSubscriber;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.netty.body.AvailableNettyByteBody;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.handler.OutboundAccess;
import io.micronaut.http.server.netty.handler.RequestHandler;
import io.micronaut.web.router.DefaultUrlRouteInfo;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    final RequestArgumentSatisfier requestArgumentSatisfier;
    final Supplier<ExecutorService> ioExecutorSupplier;
    final boolean multipartEnabled;
    final MessageBodyHandlerRegistry messageBodyHandlerRegistry;
    ExecutorService ioExecutor;
    final ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher;
    final RouteExecutor routeExecutor;
    final ConversionService conversionService;
    /**
     * This is set to {@code true} if <i>any</i> {@link HttpPipelineBuilder} has a logging handler.
     * When this is not set, we can do a shortcut for performance.
     */
    boolean supportLoggingHandler = false;

    /**
     * @param serverConfiguration          The Netty HTTP server configuration
     * @param embeddedServerContext        The embedded server context
     * @param ioExecutor                   The IO executor
     * @param terminateEventPublisher      The terminate event publisher
     * @param conversionService            The conversion service
     */
    RoutingInBoundHandler(
        NettyHttpServerConfiguration serverConfiguration,
        NettyEmbeddedServices embeddedServerContext,
        Supplier<ExecutorService> ioExecutor,
        ApplicationEventPublisher<HttpRequestTerminatedEvent> terminateEventPublisher,
        ConversionService conversionService) {
        this.staticResourceResolver = embeddedServerContext.getStaticResourceResolver();
        this.messageBodyHandlerRegistry = embeddedServerContext.getMessageBodyHandlerRegistry();
        this.ioExecutorSupplier = ioExecutor;
        this.requestArgumentSatisfier = embeddedServerContext.getRequestArgumentSatisfier();
        this.serverConfiguration = serverConfiguration;
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
                        LOG.error("Error publishing request terminated event: {}", e.getMessage(), e);
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
                LOG.debug("Swallowed an IOException caused by client connectivity: {}", cause.getMessage(), cause);
            }
            return;
        }

        if (cause instanceof SSLException || cause.getCause() instanceof SSLException || cause instanceof DecompressionException) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Micronaut Server Error - No request state present. Cause: {}", cause.getMessage(), cause);
            }
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Micronaut Server Error - No request state present. Cause: {}", cause.getMessage(), cause);
            }
        }
    }

    @Override
    public void accept(ChannelHandlerContext ctx, io.netty.handler.codec.http.HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
        NettyHttpRequest<Object> mnRequest = new NettyHttpRequest<>(request, body, ctx, conversionService, serverConfiguration);
        if (serverConfiguration.isValidateUrl()) {
            try {
                mnRequest.getUri();
            } catch (IllegalArgumentException e) {
                body.close();

                // invalid URI
                NettyHttpRequest<Object> errorRequest = new NettyHttpRequest<>(
                    new DefaultHttpRequest(request.protocolVersion(), request.method(), "/"),
                    AvailableNettyByteBody.empty(),
                    ctx,
                    conversionService,
                    serverConfiguration
                );
                outboundAccess.attachment(errorRequest);
                try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(errorRequest)).propagate()) {
                    new NettyRequestLifecycle(this, outboundAccess).handleException(errorRequest, e.getCause() == null ? e : e.getCause());
                }
                return;
            }
        }
        if (supportLoggingHandler && ctx.pipeline().get(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER) != null) {
            // Micronaut Session needs this to extract values from the Micronaut Http Request for logging
            AttributeKey<NettyHttpRequest> key = AttributeKey.valueOf(NettyHttpRequest.class.getSimpleName());
            ctx.channel().attr(key).set(mnRequest);
        }
        outboundAccess.attachment(mnRequest);
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(mnRequest)).propagate()) {
            new NettyRequestLifecycle(this, outboundAccess).handleNormal(mnRequest);
        }
    }

    public void writeResponse(OutboundAccess outboundAccess,
                              NettyHttpRequest<?> nettyHttpRequest,
                              HttpResponse<?> response,
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
                try {
                    response = routeExecutor.createDefaultErrorResponse(nettyHttpRequest, e);
                    encodeHttpResponse(
                        outboundAccess,
                        nettyHttpRequest,
                        response,
                        response.body()
                    );
                } catch (Throwable f) {
                    f.addSuppressed(e);
                    outboundAccess.closeAfterWrite();
                    try {
                        outboundAccess.writeFull(new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            Unpooled.EMPTY_BUFFER
                        ));
                    } catch (Throwable g) {
                        f.addSuppressed(g);
                    }
                    LOG.warn("Failed to encode error response", f);
                }
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
        OutboundAccess outboundAccess,
        NettyHttpRequest<?> nettyRequest,
        HttpResponse<?> httpResponse,
        Object body) {
        MutableHttpResponse<?> response = httpResponse.toMutableResponse();
        if (nettyRequest.getMethod() != HttpMethod.HEAD && body != null) {
            Object routeInfoO = response.getAttribute(HttpAttributes.ROUTE_INFO).orElse(null);
            // usually this is a UriRouteInfo, avoid scalability issues here
            @SuppressWarnings("unchecked") final RouteInfo<Object> routeInfo = (RouteInfo<Object>) (routeInfoO instanceof DefaultUrlRouteInfo<?, ?> uri ? uri : (RouteInfo<?>) routeInfoO);

            if (Publishers.isConvertibleToPublisher(body)) {
                response.body(null);
                DelegateStreamedHttpResponse streamedResponse = new DelegateStreamedHttpResponse(
                    toNettyResponse(response),
                    mapToHttpContent(nettyRequest, response, body, routeInfo, nettyRequest.getChannelHandlerContext())
                );
                writeStreamedWithErrorHandling(nettyRequest, outboundAccess, streamedResponse);
                return;
            }

            // avoid checkcast for MessageBodyWriter interface here
            Object o = response.getBodyWriter().orElse(null);
            MessageBodyWriter<Object> messageBodyWriter = o instanceof NettyJsonHandler njh ? njh : (MessageBodyWriter<Object>) o;
            MediaType responseMediaType = response.getContentType().orElse(null);
            Argument<Object> responseBodyType;
            if (routeInfo != null) {
                responseBodyType = (Argument<Object>) routeInfo.getResponseBodyType();
            } else {
                responseBodyType = Argument.of((Class<Object>) body.getClass());
            }
            if (responseMediaType == null) {
                // perf: check for common body types
                //noinspection ConditionCoveredByFurtherCondition
                if (!(body instanceof String) && !(body instanceof byte[]) && body instanceof MediaTypeProvider mediaTypeProvider) {
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
                    .findWriter(responseBodyType, Collections.singletonList(responseMediaType))
                    .orElse(null);
            }
            if (messageBodyWriter == null || !responseBodyType.isInstance(body) || !messageBodyWriter.isWriteable(responseBodyType, responseMediaType)) {
                responseBodyType = Argument.ofInstance(body);
                messageBodyWriter = this.messageBodyHandlerRegistry
                    .findWriter(responseBodyType, List.of(responseMediaType))
                    .orElse(null);
            }
            NettyBodyWriter<Object> closure = wrap(messageBodyWriter);
            closeConnectionIfError(response, nettyRequest, outboundAccess);
            if (closure.isBlocking()) {
                MediaType finalResponseMediaType = responseMediaType;
                Argument<Object> finalResponseBodyType = responseBodyType;
                getIoExecutor().execute(() -> writeNettyMessageBody(nettyRequest, (MutableHttpResponse<Object>) response, finalResponseBodyType, finalResponseMediaType, body, closure, outboundAccess, true));
            } else {
                writeNettyMessageBody(nettyRequest, (MutableHttpResponse<Object>) response, responseBodyType, responseMediaType, body, closure, outboundAccess, false);
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
        NettyHttpRequest<?> nettyRequest,
        MutableHttpResponse<Object> response,
        Argument<Object> responseBodyType,
        MediaType mediaType,
        Object body,
        NettyBodyWriter<Object> nettyMessageBodyWriter,
        OutboundAccess outboundAccess,
        boolean onIoExecutor) {
        try {
            nettyMessageBodyWriter.writeTo(
                nettyRequest,
                response,
                responseBodyType,
                mediaType,
                body, outboundAccess);
        } catch (CodecException e) {
            final MutableHttpResponse<Object> errorResponse = (MutableHttpResponse<Object>) routeExecutor.createDefaultErrorResponse(nettyRequest, e);
            writeError(nettyRequest, outboundAccess, onIoExecutor, errorResponse);
        }
    }

    private void writeError(NettyHttpRequest<?> nettyRequest, OutboundAccess outboundAccess, boolean onIoExecutor, MutableHttpResponse<Object> errorResponse) {
        Object errorBody = errorResponse.body();
        Argument<Object> type = Argument.ofInstance(errorBody);
        MediaType errorContentType = errorResponse.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
        MessageBodyWriter<Object> messageBodyWriter = messageBodyHandlerRegistry.getWriter(type, List.of(errorContentType));
        NettyBodyWriter<Object> nettyWriter = wrap(messageBodyWriter);
        if (onIoExecutor || !nettyWriter.isBlocking()) {
            nettyWriter.writeTo(nettyRequest, errorResponse, type, errorContentType, errorBody, outboundAccess);
        } else {
            ioExecutor.execute(() -> nettyWriter.writeTo(nettyRequest, errorResponse, type, errorContentType, errorBody, outboundAccess));
        }
    }

    private Flux<HttpContent> mapToHttpContent(NettyHttpRequest<?> request,
                                               MutableHttpResponse<?> response,
                                               Object body,
                                               RouteInfo<Object> routeInfo,
                                               ChannelHandlerContext context) {
        MediaType mediaType = response.getContentType().orElse(null);
        NettyByteBufferFactory byteBufferFactory = new NettyByteBufferFactory(context.alloc());
        Flux<Object> bodyPublisher = Flux.from(Publishers.convertToPublisher(conversionService, body));
        Flux<HttpContent> httpContentPublisher;
        boolean isJson = false;
        if (routeInfo != null) {
            if (mediaType == null) {
                mediaType = routeExecutor.resolveDefaultResponseContentType(request, routeInfo);
            }
            isJson = mediaType != null &&
                mediaType.getExtension().equals(MediaType.EXTENSION_JSON) && routeInfo.isResponseBodyJsonFormattable();
            MediaType finalMediaType = mediaType;
            httpContentPublisher = bodyPublisher.concatMap(message -> {
                MessageBodyWriter<Object> messageBodyWriter = routeInfo.getMessageBodyWriter();
                @SuppressWarnings("unchecked")
                Argument<Object> responseBodyType = (Argument<Object>) routeInfo.getResponseBodyType();

                if (messageBodyWriter == null || !responseBodyType.isInstance(message) || !messageBodyWriter.isWriteable(responseBodyType, finalMediaType)) {
                    responseBodyType = Argument.ofInstance(message);
                    messageBodyWriter = messageBodyHandlerRegistry.getWriter(responseBodyType, List.of(finalMediaType));
                }
                return writeAsync(
                    messageBodyWriter,
                    responseBodyType,
                    finalMediaType,
                    message,
                    response.getHeaders(), byteBufferFactory);
            }).map(byteBuffer -> new DefaultHttpContent((ByteBuf) byteBuffer.asNativeBuffer()));
        } else {
            MediaType finalMediaType = mediaType;
            httpContentPublisher = bodyPublisher
                .concatMap(message -> {
                    Argument<Object> type = Argument.ofInstance(message);
                    MessageBodyWriter<Object> messageBodyWriter = messageBodyHandlerRegistry.getWriter(type, finalMediaType == null ? List.of() : List.of(finalMediaType));
                    return writeAsync(messageBodyWriter, type, finalMediaType, message, response.getHeaders(), byteBufferFactory);
                })
                .map(byteBuffer -> new DefaultHttpContent((ByteBuf) byteBuffer.asNativeBuffer()));
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

    private <T> Publisher<ByteBuffer<?>> writeAsync(
        @NonNull MessageBodyWriter<T> messageBodyWriter,
        @NonNull Argument<T> type,
        @NonNull MediaType mediaType,
        T object,
        @NonNull MutableHeaders outgoingHeaders,
        @NonNull ByteBufferFactory<?, ?> bufferFactory
    ) {
        if (messageBodyWriter.isBlocking()) {
            return Mono.<ByteBuffer<?>>defer(() -> Mono.just(messageBodyWriter.writeTo(type, mediaType, object, outgoingHeaders, bufferFactory)))
                .subscribeOn(Schedulers.fromExecutor(ioExecutor));
        } else {
            return Mono.just(messageBodyWriter.writeTo(type, mediaType, object, outgoingHeaders, bufferFactory));
        }
    }

    private void writeFinalNettyResponse(MutableHttpResponse<?> message, NettyHttpRequest<?> request, OutboundAccess outboundAccess) {
        // default Connection header if not set explicitly
        closeConnectionIfError(message, request, outboundAccess);
        io.netty.handler.codec.http.HttpResponse nettyResponse = NettyHttpResponseBuilder.toHttpResponse(message);
        if (nettyResponse instanceof StreamedHttpResponse streamed) {
            writeStreamedWithErrorHandling(request, outboundAccess, streamed);
        } else {
            FullHttpResponse fullResponse = (FullHttpResponse) nettyResponse;
            outboundAccess.writeFull(fullResponse, request.getMethod() == HttpMethod.HEAD);
        }

        log(request, nettyResponse);
    }

    private void writeFinalFullNettyResponse(MutableHttpResponse<?> message,
                                             NettyHttpRequest<?> request,
                                             OutboundAccess outboundAccess,
                                             ByteBuf byteBuf) {
        // default Connection header if not set explicitly
        closeConnectionIfError(message, request, outboundAccess);
        FullHttpResponse fullResponse = NettyHttpResponseBuilder.toFullHttpResponse(message, byteBuf);
        outboundAccess.writeFull(fullResponse, request.getMethod() == HttpMethod.HEAD);
        log(request, fullResponse);
    }

    private void log(HttpRequest<?> request, io.netty.handler.codec.http.HttpResponse nettyResponse) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Response {} - {} {}",
                nettyResponse.status().code(),
                request.getMethodName(),
                request.getUri());
        }
    }

    private void writeStreamedWithErrorHandling(NettyHttpRequest<?> request, OutboundAccess outboundAccess, StreamedHttpResponse streamed) {
        LazySendingSubscriber sub = new LazySendingSubscriber(request, streamed, outboundAccess);
        streamed.subscribe(sub);
    }

    private void closeConnectionIfError(MutableHttpResponse<?> message, HttpRequest<?> request, OutboundAccess outboundAccess) {
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

    <T> NettyBodyWriter<T> wrap(MessageBodyWriter<T> closure) {
        if (closure instanceof NettyJsonHandler<T> nettyClosure) {
            // also covered by the next if, but this is slightly faster than an interface check
            return nettyClosure;
        } else if (closure instanceof NettyBodyWriter<T> nettyClosure) {
            return nettyClosure;
        } else {
            return new CompatNettyWriteClosure<>(closure);
        }
    }

    private final class CompatNettyWriteClosure<T> implements NettyBodyWriter<T> {
        private final MessageBodyWriter<T> delegate;

        CompatNettyWriteClosure(MessageBodyWriter<T> delegate) {
            assert !(delegate instanceof NettyBodyWriter) : "please create using wrap()";
            this.delegate = delegate;
        }

        @Override
        public boolean isBlocking() {
            return delegate.isBlocking();
        }

        @Override
        public void writeTo(HttpRequest<?> request, MutableHttpResponse<T> outgoingResponse, Argument<T> type, MediaType mediaType, T object, NettyWriteContext nettyContext) throws CodecException {
            NettyByteBufferFactory bufferFactory = new NettyByteBufferFactory(nettyContext.alloc());
            ByteBuffer<?> byteBuffer = delegate.writeTo(
                type,
                mediaType,
                object,
                outgoingResponse.getHeaders(),
                bufferFactory);
            ByteBuf buffer = (ByteBuf) byteBuffer.asNativeBuffer();
            writeFinalFullNettyResponse(outgoingResponse,
                (NettyHttpRequest<?>) request,
                (OutboundAccess) nettyContext,
                buffer);
        }

        @Override
        public void writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            delegate.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
        }

        @Override
        public ByteBuffer<?> writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
            return delegate.writeTo(type, mediaType, object, outgoingHeaders, bufferFactory);
        }
    }

    /**
     * This processor waits for the first item before sending the response, and handles errors if they
     * appear as the first item.
     */
    private final class LazySendingSubscriber implements Processor<HttpContent, HttpContent> {
        private static final Object COMPLETE = new Object();

        boolean headersSent = false;
        Subscription upstream;
        final DelayedSubscriber<HttpContent> downstream = new DelayedSubscriber<>();
        @Nullable
        HttpContent first;
        Object completion = null; // in case first hasn't been consumed we need to delay completion

        private final EventLoopFlow flow;
        private final NettyHttpRequest<?> request;
        private final io.netty.handler.codec.http.HttpResponse headers;
        private final OutboundAccess outboundAccess;

        private LazySendingSubscriber(NettyHttpRequest<?> request, io.netty.handler.codec.http.HttpResponse headers, OutboundAccess outboundAccess) {
            this.request = request;
            this.headers = headers;
            this.outboundAccess = outboundAccess;
            this.flow = new EventLoopFlow(request.getChannelHandlerContext().channel().eventLoop());
        }

        @Override
        public void subscribe(Subscriber<? super HttpContent> s) {
            downstream.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    HttpContent first = LazySendingSubscriber.this.first;
                    if (first != null) {
                        LazySendingSubscriber.this.first = null;
                        // onNext may trigger further request calls
                        s.onNext(first);
                        if (completion != null) {
                            if (completion == COMPLETE) {
                                s.onComplete();
                            } else {
                                s.onError((Throwable) completion);
                            }
                            return;
                        }
                        if (n != Long.MAX_VALUE) {
                            n--;
                            if (n == 0) {
                                return;
                            }
                        }
                    }
                    upstream.request(n);
                }

                @Override
                public void cancel() {
                    if (first != null) {
                        first.release();
                        first = null;
                    }
                    upstream.cancel();
                }
            });
            downstream.subscribe(s);
        }

        @Override
        public void onSubscribe(Subscription s) {
            upstream = s;
            s.request(1);
        }

        @Override
        public void onNext(HttpContent httpContent) {
            if (flow.executeNow(() -> onNext0(httpContent))) {
                onNext0(httpContent);
            }
        }

        private void onNext0(HttpContent httpContent) {
            if (headersSent) {
                downstream.onNext(httpContent);
            } else {
                first = httpContent;
                headersSent = true;
                outboundAccess.writeStreamed(headers, this);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (flow.executeNow(() -> onError0(t))) {
                onError0(t);
            }
        }

        private void onError0(Throwable t) {
            if (headersSent) {
                // nothing we can do
                if (first != null) {
                    completion = t;
                } else {
                    downstream.onError(t);
                }
            } else {
                // limited error handling
                MutableHttpResponse<?> response;
                if (t instanceof HttpStatusException hse) {
                    response = HttpResponse.status(hse.getStatus());
                    if (hse.getBody().isPresent()) {
                        response.body(hse.getBody().get());
                    } else if (hse.getMessage() != null) {
                        response.body(hse.getMessage());
                    }
                } else {
                    response = routeExecutor.createDefaultErrorResponse(request, t);
                }
                encodeHttpResponse(
                    outboundAccess,
                    request,
                    response,
                    response.body()
                );
            }
        }

        @Override
        public void onComplete() {
            if (flow.executeNow(this::onComplete0)) {
                onComplete0();
            }
        }

        private void onComplete0() {
            if (headersSent) {
                if (first != null) {
                    completion = COMPLETE;
                } else {
                    downstream.onComplete();
                }
            } else {
                headersSent = true;
                outboundAccess.writeStreamed(headers, Flux.empty());
            }
        }
    }
}
