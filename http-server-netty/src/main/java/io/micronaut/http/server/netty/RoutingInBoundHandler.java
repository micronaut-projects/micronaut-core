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
import io.micronaut.http.body.DynamicMessageBodyWriter;
import io.micronaut.http.body.MediaTypeProvider;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.body.NettyBodyWriter;
import io.micronaut.http.netty.body.NettyWriteContext;
import io.micronaut.http.netty.stream.JsonSubscriber;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.handler.PipeliningServerHandler;
import io.micronaut.http.server.netty.handler.RequestHandler;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.netty.buffer.ByteBuf;
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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
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
    final Supplier<ExecutorService> ioExecutorSupplier;
    final boolean multipartEnabled;
    final MessageBodyHandlerRegistry messageBodyHandlerRegistry;
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
            try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(errorRequest)).propagate()) {
                new NettyRequestLifecycle(this, outboundAccess, errorRequest).handleException(e.getCause() == null ? e : e.getCause());
            }
            if (request instanceof StreamedHttpRequest streamed) {
                streamed.closeIfNoSubscriber();
            } else {
                ((FullHttpRequest) request).release();
            }
            return;
        }
        outboundAccess.attachment(mnRequest);
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(mnRequest)).propagate()) {
            new NettyRequestLifecycle(this, outboundAccess, mnRequest).handleNormal();
        }
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
                writeStreamedWithErrorHandling(nettyRequest, outboundAccess, streamedResponse);
                return;
            }

            MessageBodyWriter<Object> messageBodyWriter = (MessageBodyWriter<Object>) response.getBodyWriter().orElse(null);
            MediaType responseMediaType = response.getContentType().orElse(null);
            Argument<Object> responseBodyType;
            if (routeInfo != null) {
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
                    .findWriter(responseBodyType, Collections.singletonList(responseMediaType))
                    .orElse(null);
            }

            Argument<Object> actualResponseType;
            if (messageBodyWriter == null || !responseBodyType.isInstance(body) || !messageBodyWriter.isWriteable(responseBodyType, responseMediaType)) {
                messageBodyWriter = new DynamicMessageBodyWriter(messageBodyHandlerRegistry, List.of(responseMediaType));
                actualResponseType = Argument.of((Class<Object>) body.getClass());
            } else {
                actualResponseType = responseBodyType;
            }
            NettyBodyWriter<Object> closure = wrap(messageBodyWriter);
            closeConnectionIfError(response, nettyRequest, outboundAccess);
            if (closure.isBlocking()) {
                MediaType finalResponseMediaType = responseMediaType;
                getIoExecutor().execute(() -> writeNettyMessageBody(nettyRequest, (MutableHttpResponse<Object>) response, actualResponseType, finalResponseMediaType, body, closure, outboundAccess));
            } else {
                writeNettyMessageBody(nettyRequest, (MutableHttpResponse<Object>) response, actualResponseType, responseMediaType, body, closure, outboundAccess);
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
        PipeliningServerHandler.OutboundAccess outboundAccess) {
        try {
            nettyMessageBodyWriter.writeTo(
                nettyRequest,
                response,
                responseBodyType,
                mediaType,
                body, outboundAccess);
        } catch (CodecException e) {
            final MutableHttpResponse<?> errorResponse = routeExecutor.createDefaultErrorResponse(nettyRequest, e);
            MediaType t = errorResponse.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
            //noinspection unchecked
            wrap(new DynamicMessageBodyWriter(messageBodyHandlerRegistry, List.of(t)))
                .writeTo(nettyRequest, (MutableHttpResponse<Object>) errorResponse, Argument.OBJECT_ARGUMENT, t, errorResponse.body(), outboundAccess);
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

                if (messageBodyWriter == null || !responseBodyType.isInstance(message) || !messageBodyWriter.isWriteable(responseBodyType, finalMediaType)) {
                    messageBodyWriter = new DynamicMessageBodyWriter(messageBodyHandlerRegistry, List.of(finalMediaType));
                }
                ByteBuffer<?> byteBuffer = messageBodyWriter.writeTo(
                    responseBodyType.isInstance(message) ? responseBodyType : (Argument<Object>) Argument.of(message.getClass()),
                    finalMediaType,
                    message,
                    response.getHeaders(), byteBufferFactory);
                return new DefaultHttpContent((ByteBuf) byteBuffer.asNativeBuffer());
            });
        } else {
            MediaType finalMediaType = mediaType;
            DynamicMessageBodyWriter dynamicWriter = new DynamicMessageBodyWriter(messageBodyHandlerRegistry, mediaType == null ? List.of() : List.of(mediaType));
            httpContentPublisher = bodyPublisher.map(message -> new DefaultHttpContent((ByteBuf) dynamicWriter.writeTo(Argument.OBJECT_ARGUMENT, finalMediaType, message, response.getHeaders(), byteBufferFactory).asNativeBuffer()));
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

    private void writeFinalNettyResponse(MutableHttpResponse<?> message, NettyHttpRequest<?> request, PipeliningServerHandler.OutboundAccess outboundAccess) {
        // default Connection header if not set explicitly
        closeConnectionIfError(message, request, outboundAccess);
        io.netty.handler.codec.http.HttpResponse nettyResponse = NettyHttpResponseBuilder.toHttpResponse(message);
        // close handled by HttpServerKeepAliveHandler
        if (request.getNativeRequest() instanceof StreamedHttpRequest streamed && !streamed.isConsumed()) {
            // consume incoming data
            Flux.from(streamed).subscribe(HttpContent::release);
        }
        if (nettyResponse instanceof StreamedHttpResponse streamed) {
            writeStreamedWithErrorHandling(request, outboundAccess, streamed);
        } else {
            FullHttpResponse fullResponse = (FullHttpResponse) nettyResponse;
            outboundAccess.writeFull(fullResponse, request.getMethod() == HttpMethod.HEAD);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Response {} - {} {}",
                nettyResponse.status().code(),
                ((HttpRequest<?>) request).getMethodName(),
                ((HttpRequest<?>) request).getUri());
        }
    }

    private void writeStreamedWithErrorHandling(NettyHttpRequest<?> request, PipeliningServerHandler.OutboundAccess outboundAccess, StreamedHttpResponse streamed) {
        LazySendingSubscriber sub = new LazySendingSubscriber(request, streamed, outboundAccess);
        streamed.subscribe(sub);
    }

    private void closeConnectionIfError(MutableHttpResponse<?> message, HttpRequest<?> request, PipeliningServerHandler.OutboundAccess outboundAccess) {
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
        if (closure instanceof NettyBodyWriter<T> nettyClosure) {
            return nettyClosure;
        } else {
            return new CompatNettyWriteClosure<>(closure);
        }
    }

    private final class CompatNettyWriteClosure<T> implements NettyBodyWriter<T> {
        private final MessageBodyWriter<T> delegate;

        CompatNettyWriteClosure(MessageBodyWriter<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isBlocking() {
            return delegate.isBlocking();
        }

        @Override
        public void writeTo(HttpRequest<?> request, MutableHttpResponse<T> outgoingResponse, Argument<T> type, MediaType mediaType, T object, NettyWriteContext nettyContext) throws CodecException {
            MessageBodyWriter<T> actual = delegate;
            // special case DynamicWriter: if the actual writer is a NettyBodyWriter, delegate to it
            if (delegate instanceof DynamicMessageBodyWriter dyn) {
                //noinspection unchecked
                actual = (MessageBodyWriter<T>) dyn.find((Argument<Object>) type, mediaType, object);
                if (actual instanceof NettyBodyWriter<T> nbw) {
                    nbw.writeTo(request, outgoingResponse, type, mediaType, object, nettyContext);
                    return;
                }
            }

            NettyByteBufferFactory bufferFactory = new NettyByteBufferFactory(nettyContext.alloc());
            ByteBuffer<?> byteBuffer = actual.writeTo(
                type,
                mediaType,
                object,
                outgoingResponse.getHeaders(), bufferFactory);
            outgoingResponse.body((Object) byteBuffer.asNativeBuffer());
            writeFinalNettyResponse(outgoingResponse, (NettyHttpRequest<?>) request, (PipeliningServerHandler.OutboundAccess) nettyContext);
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
        boolean headersSent = false;
        Subscription upstream;
        Subscriber<? super HttpContent> downstream;
        @Nullable
        HttpContent first;

        private final NettyHttpRequest<?> request;
        private final io.netty.handler.codec.http.HttpResponse headers;
        private final PipeliningServerHandler.OutboundAccess outboundAccess;

        private LazySendingSubscriber(NettyHttpRequest<?> request, io.netty.handler.codec.http.HttpResponse headers, PipeliningServerHandler.OutboundAccess outboundAccess) {
            this.request = request;
            this.headers = headers;
            this.outboundAccess = outboundAccess;
        }

        @Override
        public void subscribe(Subscriber<? super HttpContent> s) {
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    HttpContent first = LazySendingSubscriber.this.first;
                    if (first != null) {
                        LazySendingSubscriber.this.first = null;
                        // onNext may trigger further request calls
                        s.onNext(first);
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
            downstream = s;
        }

        @Override
        public void onSubscribe(Subscription s) {
            upstream = s;
            s.request(1);
        }

        @Override
        public void onNext(HttpContent httpContent) {
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
            if (headersSent) {
                // nothing we can do
                downstream.onError(t);
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
            if (headersSent) {
                downstream.onComplete();
            } else {
                headersSent = true;
                outboundAccess.writeStreamed(headers, Flux.empty());
            }
        }
    }
}
