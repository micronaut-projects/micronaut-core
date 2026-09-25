/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.ReactivePropagation;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.ConversionServiceAware;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.core.util.functional.ThrowingFunction;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableByteBodyHttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpRequestWrapper;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CharSequenceBodyWriter;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ContextlessMessageBodyHandlerRegistry;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.WritableBodyWriter;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.ClientAttributes;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawHttpClientSupport;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientErrorDecoder;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientExceptionUtils;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.client.exceptions.StreamResetException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.multipart.MultipartDataFactory;
import io.micronaut.http.client.netty.websocket.NettyWebSocketClientHandler;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ContextPathUtils;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.body.NettyByteBufMessageBodyHandler;
import io.micronaut.http.netty.body.NettyJsonHandler;
import io.micronaut.http.netty.body.NettyJsonStreamHandler;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.stream.DefaultStreamedHttpResponse;
import io.micronaut.http.netty.stream.JsonSubscriber;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.sse.Event;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.http.util.HttpHeadersUtil;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import io.micronaut.json.codec.JsonStreamMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.AsciiString;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Default implementation of the {@link HttpClient} interface based on Netty.
 *
 * @since 5.0
 */
@SuppressWarnings("FileLength")
@Internal
final class NettyHttpClient implements
    WebSocketClient,
    HttpClient,
    StreamingHttpClient,
    SseClient,
    ProxyHttpClient,
    RawHttpClient,
    Closeable,
    AutoCloseable {

    /**
     * Default logger, use {@link #log} where possible.
     */
    private static final Logger DEFAULT_LOG = LoggerFactory.getLogger(NettyHttpClient.class);
    /**
     * Set on a connection once a request was sent on it, to tell reused connections from new ones.
     */
    private static final AttributeKey<Boolean> REQUEST_SENT = AttributeKey.valueOf(NettyHttpClient.class, "requestSent");
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final String REDIRECT_COUNT = "micronaut.http.client.redirect-count";
    /**
     * Request attribute that disables following redirects for one exchange, see
     * {@link RawRequestOptions#isFollowRedirects()}.
     */
    private static final String NO_FOLLOW_REDIRECTS = "micronaut.http.client.raw.no-follow-redirects";
    /**
     * Request attribute that disables decompression for one exchange, see
     * {@link RawRequestOptions#isDecompress()}.
     */
    private static final String NO_DECOMPRESSION = "micronaut.http.client.raw.no-decompression";

    private MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final ByteBufferFactory<ByteBufAllocator, ByteBuf> byteBufferFactory = new NettyByteBufferFactory();

    private ConnectionManager connectionManager;

    private MessageBodyHandlerRegistry handlerRegistry;
    private final Supplier<DefaultHttpHeaders> redirectSameOriginPreserveBodyHeaders =
        SupplierUtil.memoized(() -> buildRedirectFilteredHeaders(false, true));
    private final Supplier<DefaultHttpHeaders> redirectCrossOriginPreserveBodyHeaders =
        SupplierUtil.memoized(() -> buildRedirectFilteredHeaders(true, true));
    private final Supplier<DefaultHttpHeaders> redirectSameOriginNonPreserveBodyHeaders =
        SupplierUtil.memoized(() -> buildRedirectFilteredHeaders(false, false));
    private final Supplier<DefaultHttpHeaders> redirectCrossOriginNonPreserveBodyHeaders =
        SupplierUtil.memoized(() -> buildRedirectFilteredHeaders(true, false));
    private final List<HttpFilterResolver.FilterEntry> clientFilterEntries;
    @Nullable
    private final LoadBalancer loadBalancer;
    private final HttpClientConfiguration configuration;
    @Nullable
    private final String contextPath;
    private final Charset defaultCharset;
    private final Logger log;
    private final HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver;
    private final WebSocketBeanRegistry webSocketRegistry;
    private final RequestBinderRegistry requestBinderRegistry;
    @Nullable
    private final String informationalServiceId;
    private final ConversionService conversionService;
    @Nullable
    private final ExecutorService blockingExecutor;
    @Nullable
    private final LifecycleListener lifecycleListener;

    NettyHttpClient(NettyHttpClientBuilder builder) {
        this.loadBalancer = builder.loadBalancer;
        this.configuration = builder.configuration == null ? new DefaultHttpClientConfiguration() : builder.configuration;
        this.defaultCharset = configuration.getDefaultCharset();
        if (StringUtils.isNotEmpty(builder.contextPath)) {
            if (builder.contextPath.charAt(0) != '/') {
                builder.contextPath = '/' + builder.contextPath;
            }
            this.contextPath = builder.contextPath;
        } else {
            this.contextPath = null;
        }

        this.mediaTypeCodecRegistry = builder.codecRegistry == null ? createDefaultMediaTypeRegistry() : builder.codecRegistry;
        this.handlerRegistry = builder.handlerRegistry == null ? createDefaultMessageBodyHandlerRegistry() : builder.handlerRegistry;
        this.log = configuration.getLoggerName().map(LoggerFactory::getLogger).orElse(DEFAULT_LOG);
        if (builder.filterResolver == null) {
            builder.filters();
        }
        this.filterResolver = Objects.requireNonNull(builder.filterResolver);
        if (builder.clientFilterEntries != null) {
            this.clientFilterEntries = builder.clientFilterEntries;
        } else {
            this.clientFilterEntries = filterResolver.resolveFilterEntries(
                new ClientFilterResolutionContext(null, AnnotationMetadata.EMPTY_METADATA)
            );
        }
        this.webSocketRegistry = builder.webSocketBeanRegistry;
        this.conversionService = builder.conversionService;
        this.requestBinderRegistry = builder.requestBinderRegistry == null ? new DefaultRequestBinderRegistry(conversionService) : builder.requestBinderRegistry;
        this.informationalServiceId = builder.informationalServiceId;
        this.blockingExecutor = builder.blockingExecutor;
        this.lifecycleListener = builder.lifecycleListener;

        this.connectionManager = new ConnectionManager(log, configuration, builder);
    }

    static NettyHttpClientBuilder newBuilder() {
        return new NettyHttpClientBuilder();
    }

    static boolean isAcceptEvents(io.micronaut.http.HttpRequest<?> request) {
        String acceptHeader = request.getHeaders().get(io.micronaut.http.HttpHeaders.ACCEPT);
        return acceptHeader != null && acceptHeader.equalsIgnoreCase(MediaType.TEXT_EVENT_STREAM);
    }

    /**
     * @return The configuration used by this client
     */
    public HttpClientConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * @return The client-specific logger name
     */
    public Logger getLog() {
        return log;
    }

    /**
     * Access to the connection manager, for micronaut-oracle-cloud.
     *
     * @return The connection manager of this client
     */
    public ConnectionManager connectionManager() {
        return connectionManager;
    }

    /**
     * @return The connection manager in use.
     */
    public ConnectionManager getConnectionManager() {
        return connectionManager();
    }

    void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public HttpClient start() {
        if (!isRunning()) {
            connectionManager.start();
        }
        if (lifecycleListener != null) {
            lifecycleListener.onStart(this);
        }
        return this;
    }

    @Override
    public boolean isRunning() {
        return connectionManager.isRunning();
    }

    @Override
    public HttpClient stop() {
        if (isRunning()) {
            connectionManager.shutdown();
        }
        if (lifecycleListener != null) {
            lifecycleListener.onStop(this);
        }
        return this;
    }

    @Override
    public HttpClient refresh() {
        if (isRunning()) {
            connectionManager.refresh();
        } else {
            start();
            connectionManager.refresh();
        }
        return this;
    }

    /**
     * Internal accessor used by {@link DefaultHttpClient} compatibility layer.
     *
     * @return The {@link MediaTypeCodecRegistry} used by this client
     */
    @Internal
    MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return mediaTypeCodecRegistry;
    }

    /**
     * Sets the {@link MediaTypeCodecRegistry} used by this client.
     *
     * @param mediaTypeCodecRegistry The registry to use. Should not be null
     */
    @Internal
    void setMediaTypeCodecRegistry(MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        if (mediaTypeCodecRegistry != null) {
            this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        }
    }

    /**
     * Get the handler registry for this client.
     *
     * @return The handler registry
     */
    @Internal
    MessageBodyHandlerRegistry getHandlerRegistry() {
        return handlerRegistry;
    }

    /**
     * Set the handler registry for this client.
     *
     * @param handlerRegistry The handler registry
     */
    @Internal
    void setHandlerRegistry(MessageBodyHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return new BlockingHttpClient() {

            @Override
            public void close() {
                NettyHttpClient.this.close();
            }

            @Override
            public <I, O, E> HttpResponse<O> exchange(io.micronaut.http.HttpRequest<I> request, @Nullable Argument<O> bodyType, Argument<E> errorType) {
                if (!configuration.isAllowBlockEventLoop() && Thread.currentThread() instanceof FastThreadLocalThread) {
                    throw new HttpClientException("""
                        You are trying to run a BlockingHttpClient operation on a netty event \
                        loop thread. This is a common cause for bugs: Event loops should \
                        never be blocked. You can either mark your controller as \
                        @ExecuteOn(TaskExecutors.BLOCKING), or use the reactive HTTP client \
                        to resolve this bug. There is also a configuration option to \
                        disable this check if you are certain a blocking operation is fine \
                        here.""");
                }
                BlockHint blockHint = BlockHint.willBlockThisThread();
                return Objects.requireNonNull(NettyHttpClient.this.exchange(request, bodyType, errorType, blockHint).block(),
                    "The blocking HTTP client returned a null response");
                // We don't have to release client response buffer
            }

            @Override
            public <I, O, E> O retrieve(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
                // mostly copied from super method, but with customizeException

                HttpResponse<O> response = exchange(request, bodyType, errorType);
                if (HttpStatus.class.isAssignableFrom(bodyType.getType())) {
                    return (O) response.getStatus();
                } else {
                    Optional<O> body = response.getBody();
                    if (body.isEmpty() && response.getBody(Argument.of(byte[].class)).isPresent()) {
                        throw decorate(new HttpClientResponseException(
                            "Failed to decode the body for the given content type [%s]".formatted(response.getContentType().orElse(null)),
                            response
                        ));
                    } else {
                        return body.orElseThrow(() -> decorate(new HttpClientResponseException(
                            "Empty body",
                            response
                        )));
                    }
                }
            }

            @Override
            public boolean isRunning() {
                return NettyHttpClient.this.isRunning();
            }

            @Override
            public BlockingHttpClient start() {
                return NettyHttpClient.this.start().toBlocking();
            }

            @Override
            public BlockingHttpClient stop() {
                return NettyHttpClient.this.stop().toBlocking();
            }
        };
    }

    private <I> MutableHttpRequest<?> toMutableRequest(io.micronaut.http.HttpRequest<I> request) {
        return MutableHttpRequestWrapper.wrapIfNecessary(conversionService, request);
    }

    @SuppressWarnings("SubscriberImplementation")
    @Override
    public <I> Publisher<Event<ByteBuffer<?>>> eventStream(io.micronaut.http.HttpRequest<I> request) {
        setupConversionService(request);
        return eventStreamOrError(request, null);
    }

    private <I> Publisher<Event<ByteBuffer<?>>> eventStreamOrError(io.micronaut.http.HttpRequest<I> request, @Nullable Argument<?> errorType) {

        if (request instanceof MutableHttpRequest<?> httpRequest) {
            httpRequest.accept(MediaType.TEXT_EVENT_STREAM_TYPE);
        }

        return Flux.create(emitter ->
            dataStream(request, errorType).subscribe(new Subscriber<>() {
                @Nullable
                private Subscription dataSubscription;
                @Nullable
                private CurrentEvent currentEvent;

                @Override
                public void onSubscribe(Subscription s) {
                    this.dataSubscription = s;
                    Disposable cancellable = s::cancel;
                    emitter.onCancel(cancellable);
                    if (!emitter.isCancelled() && emitter.requestedFromDownstream() > 0) {
                        // request the first chunk
                        dataSubscription.request(1);
                    }
                }

                @Override
                public void onNext(ByteBuffer<?> buffer) {

                    try {
                        int len = buffer.readableBytes();

                        // a length of zero indicates the start of a new event
                        // emit the current event
                        if (len == 0) {
                            try {
                                Event event = Event.of(byteBufferFactory.wrap(Objects.requireNonNull(currentEvent).data))
                                    .name(currentEvent.name)
                                    .retry(currentEvent.retry)
                                    .id(currentEvent.id);
                                emitter.next(
                                    event
                                );
                            } finally {
                                currentEvent = null;
                            }
                        } else {
                            if (currentEvent == null) {
                                currentEvent = new CurrentEvent();
                            }
                            int colonIndex = buffer.indexOf((byte) ':');
                            // SSE comments start with colon, so skip
                            if (colonIndex > 0) {
                                // obtain the type
                                String type = buffer.slice(0, colonIndex).toString(StandardCharsets.UTF_8).trim();
                                int fromIndex = colonIndex + 1;
                                // skip the white space before the actual data
                                if (buffer.getByte(fromIndex) == ((byte) ' ')) {
                                    fromIndex++;
                                }
                                if (fromIndex < len) {
                                    int toIndex = len - fromIndex;
                                    switch (type) {
                                        case "data" -> {
                                            ByteBuffer<?> content = buffer.slice(fromIndex, toIndex);
                                            byte[] d = currentEvent.data;
                                            if (d.length == 0) {
                                                currentEvent.data = content.toByteArray();
                                            } else {
                                                currentEvent.data = ArrayUtils.concat(d, content.toByteArray());
                                            }
                                        }
                                        case "id" -> {
                                            ByteBuffer<?> id = buffer.slice(fromIndex, toIndex);
                                            currentEvent.id = id.toString(StandardCharsets.UTF_8).trim();
                                        }
                                        case "event" -> {
                                            ByteBuffer<?> event = buffer.slice(fromIndex, toIndex);
                                            currentEvent.name = event.toString(StandardCharsets.UTF_8).trim();
                                        }
                                        case "retry" -> {
                                            ByteBuffer<?> retry = buffer.slice(fromIndex, toIndex);
                                            String text = retry.toString(StandardCharsets.UTF_8);
                                            if (!StringUtils.isEmpty(text)) {
                                                currentEvent.retry = Duration.ofMillis(Long.parseLong(text));
                                            }
                                        }
                                        default -> {
                                            // ignore message
                                        }
                                    }
                                }
                            }
                        }

                        if (emitter.requestedFromDownstream() > 0 && !emitter.isCancelled()) {
                            Objects.requireNonNull(dataSubscription).request(1);
                        }
                    } catch (Throwable e) {
                        onError(e);
                    } finally {
                        if (buffer instanceof ReferenceCounted counted) {
                            counted.release();
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Objects.requireNonNull(dataSubscription).cancel();
                    if (t instanceof HttpClientException) {
                        emitter.error(t);
                    } else {
                        emitter.error(decorate(new HttpClientException("Error consuming Server Sent Events: " + t.getMessage(), t)));
                    }
                }

                @Override
                public void onComplete() {
                    emitter.complete();
                }
            }), FluxSink.OverflowStrategy.BUFFER);
    }

    private static <T> Mono<T> toMono(ExecutionFlow<T> flow, PropagatedContext context) {
        return Mono.from(ReactivePropagation.propagate(context, ReactiveExecutionFlow.toPublisher(flow)));
    }

    @Override
    public <I, B> Publisher<Event<B>> eventStream(io.micronaut.http.HttpRequest<I> request,
                                                  Argument<B> eventType) {
        setupConversionService(request);
        return eventStream(request, eventType, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, B> Publisher<Event<B>> eventStream(io.micronaut.http.HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType) {
        setupConversionService(request);
        MessageBodyReader<B> reader = handlerRegistry.getReader(eventType, List.of(MediaType.APPLICATION_JSON_TYPE));
        return Flux.from(eventStreamOrError(request, errorType)).map(byteBufferEvent -> {
            ByteBuffer<?> data = byteBufferEvent.getData();

            B decoded = reader.read(eventType, MediaType.APPLICATION_JSON_TYPE, request.getHeaders(), data);
            return Event.of(byteBufferEvent, Objects.requireNonNull(decoded));
        });
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(io.micronaut.http.HttpRequest<I> request) {
        setupConversionService(request);
        return dataStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(io.micronaut.http.HttpRequest<I> request, @Nullable Argument<?> errorType) {
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return new MicronautFlux<>(toMono(resolveRequestURI(request), propagatedContext)
            .flatMapMany(target -> dataStreamImpl(toMutableRequest(request), errorType, propagatedContext, target))
            .map(bb -> {
                if (bb.asNativeBuffer() instanceof ByteBuf byteBuf && byteBuf.refCnt() > 1) {
                    // if we aren't the exclusive owner of this buffer, we need to detect whether
                    // the downstream consumer releases it or not. For that, we need our own
                    // refCnt. A composite buffer provides that.
                    CompositeByteBuf composite = byteBuf.alloc().compositeBuffer(1);
                    composite.addComponent(true, byteBuf);
                    return byteBufferFactory.wrap(composite);
                } else {
                    return bb;
                }
            }))
            .doAfterNext(buffer -> {
                Object o = buffer.asNativeBuffer();
                if (o instanceof ByteBuf byteBuf) {
                    if (byteBuf.refCnt() > 0) {
                        ReferenceCountUtil.safeRelease(byteBuf);
                    }
                }
            });
    }

    @Override
    public <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(io.micronaut.http.HttpRequest<I> request) {
        return exchangeStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(io.micronaut.http.HttpRequest<I> request, Argument<?> errorType) {
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return new MicronautFlux<>(toMono(resolveRequestURI(request), propagatedContext)
            .flatMapMany(target -> exchangeStreamImpl(propagatedContext, toMutableRequest(request), errorType, target)))
            .doAfterNext(byteBufferHttpResponse -> {
                ByteBuffer<?> buffer = byteBufferHttpResponse.body();
                if (buffer instanceof ReferenceCounted counted) {
                    counted.release();
                }
            });
    }

    @Override
    public <I, O> Publisher<O> jsonStream(io.micronaut.http.HttpRequest<I> request, Argument<O> type) {
        return jsonStream(request, type, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(io.micronaut.http.HttpRequest<I> request, Argument<O> type, Argument<?> errorType) {
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return Flux.from(toMono(resolveRequestURI(request), propagatedContext)
            .flatMapMany(target -> jsonStreamImpl(propagatedContext, toMutableRequest(request), type, errorType, target)));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I> Publisher<Map<String, Object>> jsonStream(io.micronaut.http.HttpRequest<I> request) {
        return (Publisher) jsonStream(request, Map.class);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(io.micronaut.http.HttpRequest<I> request, Class<O> type) {
        setupConversionService(request);
        return jsonStream(request, Argument.of(type));
    }

    @Override
    public <I, O, E> Publisher<HttpResponse<O>> exchange(io.micronaut.http.HttpRequest<I> request, @Nullable Argument<O> bodyType, Argument<E> errorType) {
        return Flux.defer(() -> exchange(request, bodyType, errorType, null).flux())
            // some tests expect flux...
            ;
    }

    /**
     * The exchange request.
     *
     * @param request   The request
     * @param bodyType  The body argument
     * @param errorType The error argument
     * @param <I>       The request type
     * @param <O>       The body type
     * @param <E>       The error type
     * @return The execution flow
     */
    public <I, O, E> ExecutionFlow<HttpResponse<O>> exchangeFlow(io.micronaut.http.HttpRequest<I> request,
                                                                 @Nullable Argument<O> bodyType,
                                                                 Argument<E> errorType) {
        return exchangeFlow(request, bodyType, errorType, null);
    }

    private <I, O, E> Mono<HttpResponse<O>> exchange(io.micronaut.http.HttpRequest<I> request, @Nullable Argument<O> bodyType, Argument<E> errorType, @Nullable BlockHint blockHint) {
        ExecutionFlow<HttpResponse<O>> flow = exchangeFlow(request, bodyType, errorType, blockHint);
        return toMono(flow, PropagatedContext.getOrEmpty());
    }

    private <I, O, E> ExecutionFlow<HttpResponse<O>> exchangeFlow(io.micronaut.http.HttpRequest<I> request,
                                                                  @Nullable Argument<O> bodyType,
                                                                  Argument<E> errorType,
                                                                  @Nullable BlockHint blockHint) {
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        // if a connection is available immediately, we can use its executor for the timeout
        // instead of a random executor for the whole group
        AtomicReference<ScheduledExecutorService> scheduler = new AtomicReference<>(connectionManager.getGroup());
        ExecutionFlow<HttpResponse<O>> flow = resolveRequestURI(request).flatMap(target -> {
            MutableHttpRequest<?> mutableRequest = toMutableRequest(request).uri(target.uri());
            //noinspection unchecked
            return sendRequestWithRedirects(
                propagatedContext,
                scheduler,
                blockHint,
                mutableRequest,
                target.instance(),
                (req, resp) -> InternalByteBody.bufferFlow(resp.byteBody())
                    .onErrorResume(t -> ExecutionFlow.error(handleResponseError(mutableRequest, target.instance(), t)))
                    .flatMap(av -> handleExchangeResponse(bodyType, errorType, resp, av))
            ).map(r -> (HttpResponse<O>) r);
        });

        Duration requestTimeout = configuration.getRequestTimeout();
        if (requestTimeout == null) {
            // for compatibility
            requestTimeout = configuration.getReadTimeout()
                .filter(d -> !d.isNegative())
                .map(d -> d.plusSeconds(1)).orElse(null);
        }
        if (requestTimeout != null) {
            if (!requestTimeout.isNegative()) {
                flow = flow.timeout(requestTimeout, Objects.requireNonNull(scheduler.get()), null)
                    .onErrorResume(throwable -> {
                        if (throwable instanceof TimeoutException) {
                            return ExecutionFlow.error(ReadTimeoutException.TIMEOUT_EXCEPTION);
                        }
                        return ExecutionFlow.error(throwable);
                    });
            }
        }
        return flow;
    }

    private <O, E> ExecutionFlow<FullNettyClientHttpResponse<O>> handleExchangeResponse(@Nullable Argument<O> bodyType, Argument<E> errorType, NettyClientByteBodyResponse resp, CloseableAvailableByteBody av) {
        ByteBuf buf = NettyByteBodyFactory.toByteBuf(av);
        DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(
            resp.nettyResponse.protocolVersion(),
            resp.nettyResponse.status(),
            buf,
            resp.nettyResponse.headers(),
            EmptyHttpHeaders.INSTANCE
        );

        try {
            if (log.isTraceEnabled()) {
                traceBody("Response", fullHttpResponse.content());
            }

            boolean convertBodyWithBodyType = shouldConvertWithBodyType(fullHttpResponse, this.configuration, bodyType, errorType);
            FullNettyClientHttpResponse<O> response = new FullNettyClientHttpResponse<>(fullHttpResponse, handlerRegistry, bodyType, convertBodyWithBodyType, conversionService);

            if (convertBodyWithBodyType) {
                return ExecutionFlow.just(response);
            } else { // error flow
                try {
                    return ExecutionFlow.error(makeErrorFromRequestBody(errorType, fullHttpResponse.status(), response));
                } catch (HttpClientResponseException t) {
                    return ExecutionFlow.error(t);
                } catch (Exception t) {
                    return ExecutionFlow.error(makeErrorBodyParseError(fullHttpResponse, t));
                }
            }
        } catch (HttpClientResponseException t) {
            return ExecutionFlow.error(t);
        } catch (Exception t) {
            FullNettyClientHttpResponse<Object> response = new FullNettyClientHttpResponse<>(
                fullHttpResponse,
                handlerRegistry,
                null,
                false,
                conversionService
            );
            HttpClientResponseException clientResponseError = decorate(new HttpClientResponseException(
                "Error decoding HTTP response body: " + t.getMessage(),
                t,
                response,
                new HttpClientErrorDecoder() {
                    @Override
                    public Argument<?> getErrorType(MediaType mediaType) {
                        return errorType;
                    }
                }
            ));
            return ExecutionFlow.error(clientResponseError);
        } finally {
            fullHttpResponse.release();
        }
    }

    @Override
    public <I, O, E> Publisher<O> retrieve(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        setupConversionService(request);
        // mostly same as default impl, but with exception customization
        Flux<HttpResponse<O>> exchange = Flux.from(exchange(request, bodyType, errorType));
        if (bodyType.getType() == void.class) {
            // exchange() returns a HttpResponse<Void>, we can't map the Void body properly, so just drop it and complete
            return (Publisher<O>) exchange.ignoreElements();
        }
        return exchange.map(response -> {
            if (bodyType.getType() == HttpStatus.class) {
                return (O) response.getStatus();
            } else {
                Optional<O> body = response.getBody();
                if (body.isEmpty() && response.getBody(byte[].class).isPresent()) {
                    throw decorate(new HttpClientResponseException(
                        "Failed to decode the body for the given content type [%s]".formatted(response.getContentType().orElse(null)),
                        response
                    ));
                } else {
                    return body.orElseThrow(() -> decorate(new HttpClientResponseException(
                        "Empty body",
                        response
                    )));
                }
            }
        });
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, MutableHttpRequest<?> request) {
        setupConversionService(request);
        return toMono(resolveRequestURI(request), PropagatedContext.getOrEmpty()).flux()
            .switchMap(target -> connectWebSocket(target.uri(), request, clientEndpointType, null));
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters) {
        WebSocketBean<T> webSocketBean = webSocketRegistry.getWebSocket(clientEndpointType);
        String uri = webSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class).orElse("/ws");
        uri = UriTemplate.of(uri).expand(parameters);
        MutableHttpRequest<Object> request = io.micronaut.http.HttpRequest.GET(uri);
        return toMono(resolveRequestURI(request), PropagatedContext.getOrEmpty()).flux()
            .switchMap(target -> connectWebSocket(target.uri(), request, clientEndpointType, webSocketBean));

    }

    @Override
    public void close() {
        stop();
    }

    private <T> Publisher<T> connectWebSocket(URI uri, MutableHttpRequest<?> request, Class<T> clientEndpointType, @Nullable WebSocketBean<T> webSocketBean) {
        RequestKey requestKey;
        try {
            requestKey = new RequestKey(this, uri);
        } catch (HttpClientException e) {
            return Flux.error(e);
        }

        if (webSocketBean == null) {
            webSocketBean = webSocketRegistry.getWebSocket(clientEndpointType);
        }

        WebSocketVersion protocolVersion = webSocketBean.getBeanDefinition().enumValue(ClientWebSocket.class, "version", WebSocketVersion.class).orElse(WebSocketVersion.V13);
        int maxFramePayloadLength = webSocketBean.messageMethod()
            .map(m -> m.intValue(OnMessage.class, "maxPayloadLength")
                .orElse(65536)).orElse(65536);
        String subprotocol = webSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class, "subprotocol").orElse(StringUtils.EMPTY_STRING);
        URI webSocketURL = UriBuilder.of(uri)
            .scheme(!requestKey.isSecure() ? "ws" : "wss")
            .host(requestKey.getHost())
            .port(requestKey.getPort())
            .build();

        MutableHttpHeaders headers = request.getHeaders();
        HttpHeaders customHeaders = EmptyHttpHeaders.INSTANCE;
        if (headers instanceof NettyHttpHeaders httpHeaders) {
            customHeaders = httpHeaders.getNettyHeaders();
        }
        if (StringUtils.isNotEmpty(subprotocol)) {
            NettyHttpHeaders.validateHeader("Sec-WebSocket-Protocol", subprotocol);
            customHeaders.add("Sec-WebSocket-Protocol", subprotocol);
        }

        NettyWebSocketClientHandler<T> handler = new NettyWebSocketClientHandler<>(
            request,
            webSocketBean,
            WebSocketClientHandshakerFactory.newHandshaker(
                webSocketURL, protocolVersion, subprotocol, true, customHeaders, maxFramePayloadLength),
            requestBinderRegistry,
            mediaTypeCodecRegistry,
            handlerRegistry,
            conversionService);

        if (!isRunning()) {
            return Mono.error(decorate(new HttpClientException("The client is closed, unable to connect for websocket.")));
        }

        return connectionManager.connectForWebsocket(requestKey, handler)
            .then(handler.getHandshakeCompletedMono());
    }

    private <I> Flux<HttpResponse<ByteBuffer<?>>> exchangeStreamImpl(PropagatedContext propagatedContext, MutableHttpRequest<I> request, Argument<?> errorType, ResolvedTarget target) {
        Flux<HttpResponse<?>> streamResponsePublisher = toMono(buildStreamExchange(propagatedContext, request, target, errorType), propagatedContext).flux();
        return streamResponsePublisher.switchMap(response -> {
            StreamedHttpResponse streamedHttpResponse = NettyHttpResponseBuilder.toStreamResponse(response);
            Flux<HttpContent> httpContentReactiveSequence = Flux.from(streamedHttpResponse);
            return httpContentReactiveSequence
                .filter(message -> !(message.content() instanceof EmptyByteBuf))
                .map(message -> {
                    ByteBuf byteBuf = message.content();
                    if (log.isTraceEnabled()) {
                        log.trace("HTTP Client Streaming Response Received Chunk (length: {}) for Request: {} {}",
                            byteBuf.readableBytes(), request.getMethodName(), request.getUri());
                        traceBody("Response", byteBuf);
                    }
                    ByteBuffer<?> byteBuffer = byteBufferFactory.wrap(byteBuf);
                    NettyStreamedHttpResponse<ByteBuffer<?>> thisResponse = new NettyStreamedHttpResponse<>(streamedHttpResponse, conversionService);
                    thisResponse.setBody(byteBuffer);
                    return (HttpResponse<ByteBuffer<?>>) new HttpResponseWrapper<>(thisResponse);
                });
        });
    }

    private <I, O> Flux<O> jsonStreamImpl(PropagatedContext propagatedContext, MutableHttpRequest<I> request, Argument<O> type, Argument<?> errorType, ResolvedTarget target) {
        return toMono(buildStreamExchange(propagatedContext, request, target, errorType), propagatedContext).flux().switchMap(response -> {
            if (!(response instanceof NettyStreamedHttpResponse)) {
                throw new IllegalStateException("Response has been wrapped in non streaming type. Do not wrap the response in client filters for stream requests");
            }

            StreamedHttpResponse streamResponse = NettyHttpResponseBuilder.toStreamResponse(response);

            // could also be application/json, in which case we will stream an array
            MediaType mediaType = response.getContentType().orElse(MediaType.APPLICATION_JSON_STREAM_TYPE);
            ChunkedMessageBodyReader<O> reader = (ChunkedMessageBodyReader<O>) handlerRegistry.getReader(type, List.of(mediaType));
            return reader.readChunked(type, mediaType, response.getHeaders(), Flux.from(streamResponse).map(c -> NettyByteBufferFactory.DEFAULT.wrap(c.content())));
        });
    }

    private <I> Flux<ByteBuffer<?>> dataStreamImpl(MutableHttpRequest<I> request, @Nullable Argument<?> errorType, PropagatedContext propagatedContext, ResolvedTarget target) {
        Flux<HttpResponse<?>> streamResponsePublisher = toMono(buildStreamExchange(propagatedContext, request, target, errorType), propagatedContext).flux();
        Function<HttpContent, ByteBuffer<?>> contentMapper = message -> {
            ByteBuf byteBuf = message.content();
            return byteBufferFactory.wrap(byteBuf);
        };
        return streamResponsePublisher.switchMap(response -> {
            if (!(response instanceof NettyStreamedHttpResponse)) {
                throw new IllegalStateException("Response has been wrapped in non streaming type. Do not wrap the response in client filters for stream requests");
            }
            NettyStreamedHttpResponse nettyStreamedHttpResponse = (NettyStreamedHttpResponse) response;
            Flux<HttpContent> httpContentReactiveSequence = Flux.from(nettyStreamedHttpResponse.getNettyResponse());
            return httpContentReactiveSequence
                .filter(message -> !(message.content() instanceof EmptyByteBuf))
                .map(contentMapper);
        });
    }

    /**
     * Implementation of {@link #jsonStream}, {@link #dataStream}, {@link #exchangeStream}.
     */
    @SuppressWarnings("MagicNumber")
    private <I> ExecutionFlow<HttpResponse<?>> buildStreamExchange(
        PropagatedContext propagatedContext,
        MutableHttpRequest<I> request,
        ResolvedTarget target,
        @Nullable Argument<?> errorType) {
        return this.sendRequestWithRedirects(
            propagatedContext,
            null,
            request.uri(target.uri()),
            target.instance(),
            (req, resp) -> {
                if (resp.code() >= 400 && !shouldBufferErrorBody(errorType)) {
                    // The error body will never be consumed by the caller, so discard it right
                    // away. Otherwise the connection would stay reserved until the read timeout.
                    resp.close();
                    return ExecutionFlow.error(decorate(new HttpClientResponseException(resp.reason(), toStreamingResponse(resp, Flux.empty()))));
                }
                ByteBody bb = resp.byteBody();
                Publisher<HttpContent> body;
                if (!hasBody(resp)) {
                    resp.close();
                    body = Flux.empty();
                } else {
                    if (isAcceptEvents(req)) {
                        if (bb instanceof AvailableByteBody anbb) {
                            // same semantics as the streaming branch, but this is eager so it's more
                            // lax wrt unclosed responses.
                            ByteBuf single = NettyByteBodyFactory.toByteBuf(anbb);
                            List<ByteBuf> parts = SseSplitter.split(single);
                            parts.get(parts.size() - 1).release();
                            body = Flux.fromIterable(parts.subList(0, parts.size() - 1)).map(DefaultHttpContent::new);
                        } else {
                            body = SseSplitter.split(NettyByteBodyFactory.toByteBufs(bb), sizeLimits()).map(DefaultHttpContent::new);
                        }
                    } else {
                        body = NettyByteBodyFactory.toByteBufs(bb).map(DefaultHttpContent::new);
                    }
                }

                return readBodyOnError(errorType, ExecutionFlow.<HttpResponse<?>>just(toStreamingResponse(resp, body))
                    .flatMap(r -> handleStreamHttpError(r, true)));
            }
        );
    }

    private <B> MutableHttpResponse<B> toStreamingResponse(NettyClientByteBodyResponse resp, Publisher<HttpContent> content) {
        DefaultStreamedHttpResponse nettyResponse = new DefaultStreamedHttpResponse(
            resp.nettyResponse.protocolVersion(),
            resp.nettyResponse.status(),
            resp.getHeaders().getNettyHeaders(),
            content
        );
        return new NettyStreamedHttpResponse<>(nettyResponse, conversionService);
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(io.micronaut.http.HttpRequest<?> request) {
        return proxy(request, ProxyRequestOptions.getDefault());
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(io.micronaut.http.HttpRequest<?> request, ProxyRequestOptions options) {
        Objects.requireNonNull(options, "options");
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return Mono.defer(() -> {
            // the body bytes of a server request are claimed when the exchange starts, and
            // released when it ends, unless they were sent: e.g. when the upstream refuses the
            // connection, a streaming server request can then discard the rest of its body
            MutableHttpRequest<?> httpRequest = toProxyRequest(request);
            Mono<MutableHttpResponse<?>> response = proxy(propagatedContext, request, httpRequest, options);
            return httpRequest instanceof RawHttpRequestWrapper<?> claimed ? response.doFinally(signal -> claimed.close()) : response;
        });
    }

    private Mono<MutableHttpResponse<?>> proxy(PropagatedContext propagatedContext, io.micronaut.http.HttpRequest<?> request, MutableHttpRequest<?> httpRequest, ProxyRequestOptions options) {
        return toMono(resolveRequestURI(request)
            .flatMap(target -> {
                if (!options.isRetainHostHeader()) {
                    httpRequest.headers(headers -> headers.remove(HttpHeaderNames.HOST));
                }

                return this.sendRequestWithRedirects(
                    propagatedContext,
                    null,
                    httpRequest.uri(target.uri()),
                    target.instance(),
                    (req, resp) -> {
                        if (!hasBody(resp)) {
                            resp.close();
                            return ExecutionFlow.just(MutableByteBodyHttpResponse.of(resp, NettyByteBodyFactory.empty()));
                        }
                        // relayed without a length, like the content publisher proxied responses used to be
                        return ExecutionFlow.just(MutableByteBodyHttpResponse.of(resp, new UnknownLengthByteBody(resp.byteBody().move())));
                    }
                );
            })
            .map(HttpResponse::toMutableResponse), propagatedContext);
    }

    /**
     * The request to send for {@link #proxy}. The body bytes of a server request are relayed as
     * they are, whichever server received it.
     *
     * @param request The request to proxy
     * @return The request to send
     */
    private MutableHttpRequest<?> toProxyRequest(io.micronaut.http.HttpRequest<?> request) {
        MutableHttpRequest<?> mutableRequest = toMutableRequest(request);
        CloseableByteBody serverBody = RawHttpClientSupport.claimServerRequestBody(request);
        if (serverBody != null) {
            return new RawHttpRequestWrapper<>(conversionService, mutableRequest, serverBody);
        }
        return mutableRequest;
    }

    private void setupConversionService(io.micronaut.http.HttpRequest<?> httpRequest) {
        if (httpRequest instanceof ConversionServiceAware aware) {
            aware.setConversionService(conversionService);
        }
    }

    /**
     * @param request The request
     * @param <I>     The input type
     * @return A flow with the resolved target
     */
    <I> ExecutionFlow<ResolvedTarget> resolveRequestURI(io.micronaut.http.HttpRequest<I> request) {
        return resolveRequestURI(request, true);
    }

    /**
     * @param request            The request
     * @param includeContextPath Whether to prepend the client context path
     * @param <I>                The input type
     * @return A flow with the resolved target
     */
    <I> ExecutionFlow<ResolvedTarget> resolveRequestURI(io.micronaut.http.HttpRequest<I> request, boolean includeContextPath) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return ExecutionFlow.just(new ResolvedTarget(requestURI, null));
        } else {
            return resolveURI(request, includeContextPath);
        }
    }

    /**
     * @param parentRequest The parent request
     * @param request       The redirect location request
     * @param <I>           The input type
     * @return A flow with the resolved target
     */
    <I> ExecutionFlow<ResolvedTarget> resolveRedirectURI(io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<I> request) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return ExecutionFlow.just(new ResolvedTarget(requestURI, null));
        } else {
            if (parentRequest == null || parentRequest.getUri().getHost() == null) {
                return resolveURI(request, false);
            } else {
                URI redirectedURI = parentRequest.getUri().resolve(requestURI).normalize();
                return ExecutionFlow.just(new ResolvedTarget(redirectedURI, null));
            }
        }
    }

    /**
     * @param request The request object
     * @return The discriminator to use when selecting a server for the purposes of load balancing (defaults to {@link io.micronaut.http.HttpRequest})
     */
    Object getLoadBalancerDiscriminator(io.micronaut.http.HttpRequest<?> request) {
        return request;
    }

    /**
     * @param request            The request
     * @param requestURI         The URI of the request
     * @param requestContentType The request content type
     * @param permitsBody        Whether permits body
     * @param channel            The channel
     * @param outgoingHeaders    Headers added by the client that apply only to the outgoing
     *                           netty request, so that the caller's request is not modified
     * @return The body
     * @throws HttpPostRequestEncoder.ErrorDataEncoderException if there is an encoder exception
     */
    private CloseableByteBody buildNettyRequest(
        MutableHttpRequest<?> request,
        URI requestURI,
        MediaType requestContentType,
        boolean permitsBody,
        Channel channel,
        HttpHeaders outgoingHeaders) throws HttpPostRequestEncoder.ErrorDataEncoderException {

        NettyByteBodyFactory byteBodyFactory = new NettyByteBodyFactory(channel);
        if (!request.getHeaders().contains(HttpHeaderNames.HOST)) {
            outgoingHeaders.set(HttpHeaderNames.HOST, getHostHeader(requestURI));
        }

        if (permitsBody) {
            Optional<?> body = request.getBody();
            if (body.isPresent()) {
                if (!request.getHeaders().contains(HttpHeaderNames.CONTENT_TYPE)) {
                    MediaType mediaType = request.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
                    outgoingHeaders.set(HttpHeaderNames.CONTENT_TYPE, mediaType);
                }
            }
        }

        NettyHttpRequestBuilder nettyRequestBuilder = NettyHttpRequestBuilder.asBuilder(request);
        ByteBody direct = nettyRequestBuilder.byteBodyDirect();
        if (direct != null) {
            return direct.move();
        }

        if (permitsBody) {
            Optional<?> body = request.getBody();
            boolean hasBody = body.isPresent();
            if (requestContentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE) && hasBody && !isEncodedFormBody(body.get())) {
                Object bodyValue = body.get();
                return buildFormRequest(request, outgoingHeaders, byteBodyFactory, r -> buildFormDataRequest(r, bodyValue));
            } else if (requestContentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE) && hasBody && !isEncodedFormBody(body.get())) {
                return buildFormRequest(request, outgoingHeaders, byteBodyFactory, r -> buildMultipartRequest(r, body.get()));
            } else {
                ReadBuffer bodyContent;
                if (hasBody) {
                    Object bodyValue = body.get();
                    if (Publishers.isConvertibleToPublisher(bodyValue)) {
                        boolean isSingle = Publishers.isSingle(bodyValue.getClass());

                        Publisher<?> publisher = conversionService.convert(bodyValue, Publisher.class).orElseThrow(() ->
                            new IllegalArgumentException("Unconvertible reactive type: " + bodyValue)
                        );

                        Flux<HttpContent> requestBodyPublisher = Flux.from(publisher).map(value -> {
                            Argument<Object> type = Argument.ofInstance(value);
                            ByteBuffer<?> buffer = handlerRegistry.getWriter(type, List.of(requestContentType))
                                .writeTo(type, requestContentType, value, request.getHeaders(), byteBufferFactory);
                            return new DefaultHttpContent(((ByteBuf) buffer.asNativeBuffer()));
                        });

                        if (!isSingle && MediaType.APPLICATION_JSON_TYPE.equals(requestContentType)) {
                            requestBodyPublisher = JsonSubscriber.lift(requestBodyPublisher);
                        }

                        return byteBodyFactory.adapt(requestBodyPublisher.map(ByteBufHolder::content), nettyRequestBuilder.toHttpRequestWithoutBody().headers(), null);
                    } else if (bodyValue instanceof CharSequence sequence) {
                        bodyContent = charSequenceToByteBuf(sequence, requestContentType);
                    } else {
                        Argument<Object> type = Argument.ofInstance(bodyValue);
                        ByteBuffer<?> buffer = handlerRegistry.getWriter(type, List.of(requestContentType))
                            .writeTo(type, requestContentType, bodyValue, request.getHeaders(), byteBufferFactory);
                        bodyContent = byteBodyFactory.readBufferFactory().adapt(buffer);
                    }
                } else {
                    bodyContent = byteBodyFactory.readBufferFactory().createEmpty();
                }
                return byteBodyFactory.adapt(bodyContent);
            }
        } else {
            return NettyByteBodyFactory.empty();
        }
    }

    /**
     * A form or multipart body that is already encoded is written as is, like any other raw body.
     *
     * @param bodyValue The body value
     * @return Whether the body is already encoded
     */
    private static boolean isEncodedFormBody(Object bodyValue) {
        return bodyValue instanceof CharSequence || bodyValue instanceof byte[] || bodyValue instanceof ByteBuffer<?>;
    }

    private static boolean requiresRequestBody(HttpMethod method) {
        return method != null && (method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT) || method.equals(HttpMethod.PATCH) || method.equals(HttpMethod.QUERY));
    }

    private static boolean permitsRequestBody(HttpMethod method) {
        return method != null && (requiresRequestBody(method)
            || method.equals(HttpMethod.OPTIONS)
            || method.equals(HttpMethod.DELETE)
        );
    }

    private void completeExceptionallySafe(DelayedExecutionFlow<?> flow, Throwable exc) {
        if (!flow.tryCompleteExceptionally(exc)) {
            log.debug("Client exception suppressed because response flow already completed", exc);
        }
    }

    /**
     * Whether the body of an error response of a streaming call should be read and attached to
     * the {@link HttpClientResponseException}.
     *
     * @param errorType The error type
     * @return {@code true} if the error body should be buffered
     */
    private boolean shouldBufferErrorBody(@Nullable Argument<?> errorType) {
        return errorType != null && (errorType != HttpClient.DEFAULT_ERROR_TYPE || configuration.isBufferErrorBodyForStreaming());
    }

    private ExecutionFlow<HttpResponse<?>> readBodyOnError(@Nullable Argument<?> errorType, ExecutionFlow<HttpResponse<?>> publisher) {
        if (errorType != null && shouldBufferErrorBody(errorType)) {
            return publisher.onErrorResume(clientException -> {
                if (clientException instanceof HttpClientResponseException exception) {
                    final HttpResponse<?> response = exception.getResponse();
                    if (response instanceof NettyStreamedHttpResponse<?> streamedResponse) {
                        DelayedExecutionFlow<HttpResponse<?>> delayed = DelayedExecutionFlow.create();
                        final StreamedHttpResponse nettyResponse = streamedResponse.getNettyResponse();
                        nettyResponse.subscribe(new Subscriber<>() {
                            final CompositeByteBuf buffer = byteBufferFactory.getNativeAllocator().compositeBuffer();
                            @Nullable
                            Subscription s;

                            @Override
                            public void onSubscribe(Subscription s) {
                                this.s = s;
                                s.request(1);
                            }

                            @Override
                            public void onNext(HttpContent httpContent) {
                                buffer.addComponent(true, httpContent.content());
                                Objects.requireNonNull(s).request(1);
                            }

                            @Override
                            public void onError(Throwable t) {
                                buffer.release();
                                completeExceptionallySafe(delayed, t);
                            }

                            @Override
                            public void onComplete() {
                                try {
                                    FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(nettyResponse.protocolVersion(), nettyResponse.status(), buffer, nettyResponse.headers(), new DefaultHttpHeaders(true));
                                    boolean hasErrorType = errorType != HttpClient.DEFAULT_ERROR_TYPE;
                                    final FullNettyClientHttpResponse<Object> fullNettyClientHttpResponse = new FullNettyClientHttpResponse<>(fullHttpResponse, handlerRegistry, hasErrorType ? (Argument<Object>) errorType : null, hasErrorType, conversionService);
                                    completeExceptionallySafe(delayed, decorate(new HttpClientResponseException(
                                        fullHttpResponse.status().reasonPhrase(),
                                        null,
                                        fullNettyClientHttpResponse,
                                        hasErrorType ? new HttpClientErrorDecoder() {
                                            @Override
                                            public Argument<?> getErrorType(MediaType mediaType) {
                                                return errorType;
                                            }
                                        } : HttpClientErrorDecoder.DEFAULT
                                    )));
                                } finally {
                                    buffer.release();
                                }
                            }
                        });
                        return delayed;
                    }
                }
                return ExecutionFlow.error(clientException);
            });
        }
        return publisher;
    }

    private <I> ExecutionFlow<ResolvedTarget> resolveURI(io.micronaut.http.HttpRequest<I> request, boolean includeContextPath) {
        URI requestURI = request.getUri();
        if (loadBalancer == null) {
            return ExecutionFlow.error(decorate(new NoHostException("Request URI specifies no host to connect to")));
        }
        ExecutionFlow<ServiceInstance> selected;
        if (loadBalancer instanceof FixedLoadBalancer fixed) {
            selected = ExecutionFlow.just(fixed.getServiceInstance());
        } else {
            selected = ReactiveExecutionFlow.fromPublisher(loadBalancer.select(getLoadBalancerDiscriminator(request)));
        }

        return selected.map(server -> {
                Optional<String> authInfo = server.getMetadata().get(io.micronaut.http.HttpHeaders.AUTHORIZATION_INFO, String.class);
                if (request instanceof MutableHttpRequest<?> httpRequest && authInfo.isPresent()) {
                    httpRequest.getHeaders().auth(authInfo.get());
                }

                try {
                    return new ResolvedTarget(server.resolve(includeContextPath ? ContextPathUtils.prepend(requestURI, contextPath) : requestURI), server);
                } catch (URISyntaxException e) {
                    throw decorate(new HttpClientException("Failed to construct the request URI", e));
                }
            }
        );
    }

    private <R extends HttpResponse<?>> ExecutionFlow<R> handleStreamHttpError(
        R response,
        boolean failOnError
    ) {
        boolean errorStatus = response.code() >= 400;
        if (errorStatus && failOnError) {
            // the body is consumed by readBodyOnError, this is only reached if the error body is buffered
            return ExecutionFlow.error(decorate(new HttpClientResponseException(response.reason(), response)));
        } else {
            return ExecutionFlow.just(response);
        }
    }

    @Override
    public Publisher<? extends HttpResponse<?>> exchange(io.micronaut.http.HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread) {
        return rawExchange(request, requestBody, blockedThread, null);
    }

    @Override
    public Publisher<? extends HttpResponse<?>> exchange(io.micronaut.http.HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread, RawRequestOptions options) {
        Objects.requireNonNull(options, "options");
        return rawExchange(request, requestBody, blockedThread, options);
    }

    @Override
    public AsyncRawHttpClient toAsyncRaw() {
        return new NettyAsyncRawHttpClient(this);
    }

    private Mono<HttpResponse<?>> rawExchange(io.micronaut.http.HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread, @Nullable RawRequestOptions options) {
        CloseableByteBody body = requestBody == null ? NettyByteBodyFactory.empty() : requestBody;
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        ExecutionFlow<HttpResponse<?>> flow = rawExchangeFlow(propagatedContext, request, body, blockedThread, options);
        // doFinally: a cancelled exchange closes the body too, e.g. one that waits for a connection
        return toMono(flow, propagatedContext).doFinally(signal -> body.close());
    }

    /**
     * The flow of a raw exchange. Cancelling it before the response arrives aborts the request.
     * The caller closes the request body when the flow completes or is cancelled.
     *
     * @param propagatedContext The propagated context
     * @param request           The request metadata
     * @param requestBody       The request body
     * @param blockedThread     The thread that blocks on the response, if any
     * @param options           The per-exchange options, or {@code null} for none
     * @return The response flow
     */
    ExecutionFlow<HttpResponse<?>> rawExchangeFlow(PropagatedContext propagatedContext, io.micronaut.http.HttpRequest<?> request, CloseableByteBody requestBody, @Nullable Thread blockedThread, @Nullable RawRequestOptions options) {
        try {
            BlockHint blockHint = blockedThread == null ? null : new BlockHint(blockedThread, null);
            if (options == null) {
                return sendRawExchange(
                    propagatedContext,
                    blockHint,
                    new RawHttpRequestWrapper<>(conversionService, request.toMutableRequest(), requestBody)
                );
            }
            MutableHttpRequest<Object> rawRequest = new RawHttpRequestWrapper<>(conversionService, RawHttpClientSupport.copyRequest(request, options), requestBody);
            applyOptions(rawRequest, options);
            return RawHttpClientSupport.withResponseTimeout(sendRawExchange(
                propagatedContext,
                blockHint,
                rawRequest
            ), options.getResponseTimeout()).map(RawHttpClientSupport::toMutableResponse);
        } catch (RuntimeException | Error e) {
            requestBody.close();
            throw e;
        }
    }

    /**
     * Send a raw request. A relative request URI is resolved against the URL of this client
     * first, like the URI of any other request.
     *
     * @param propagatedContext The propagated context
     * @param blockHint         The block hint, if any
     * @param rawRequest        The raw request
     * @return The response flow
     */
    private ExecutionFlow<HttpResponse<?>> sendRawExchange(PropagatedContext propagatedContext, @Nullable BlockHint blockHint, MutableHttpRequest<?> rawRequest) {
        if (rawRequest.getUri().getScheme() != null) {
            return sendRequestWithRedirects(propagatedContext, blockHint, rawRequest, null, (req, resp) -> ExecutionFlow.just(resp));
        }
        return resolveRequestURI(rawRequest).flatMap(target -> sendRequestWithRedirects(
            propagatedContext,
            blockHint,
            rawRequest.uri(target.uri()),
            target.instance(),
            (req, resp) -> ExecutionFlow.just(resp)
        ));
    }

    private static void applyOptions(MutableHttpRequest<?> request, RawRequestOptions options) {
        if (!options.isFollowRedirects()) {
            request.setAttribute(NO_FOLLOW_REDIRECTS, Boolean.TRUE);
        }
        if (!options.isDecompress()) {
            request.setAttribute(NO_DECOMPRESSION, Boolean.TRUE);
        }
    }

    private ExecutionFlow<HttpResponse<?>> sendRequestWithRedirects(
        PropagatedContext propagatedContext,
        @Nullable BlockHint blockHint,
        MutableHttpRequest<?> request,
        @Nullable ServiceInstance instance,
        BiFunction<MutableHttpRequest<?>, NettyClientByteBodyResponse, ? extends ExecutionFlow<? extends HttpResponse<?>>> readResponse
    ) {
        return sendRequestWithRedirects(propagatedContext, new AtomicReference<>(), blockHint, request, instance, readResponse);
    }

    /**
     * This is the high-level request method. It sits above {@link #sendRawRequest} and handles
     * things like filters, error handling, response parsing, request writing.
     *
     * @param propagatedContext  The context propagated from the original client call
     * @param preferredScheduler A reference holding the preferred scheduler for timeouts. This is
     *                           replaced by the connection event loop ASAP so that callers can take
     *                           advantage of locality
     * @param blockHint          The optional block hint
     * @param request            The request to send. Must have resolved absolute URI (see {@link #resolveURI})
     * @param instance           The service instance the load balancer selected for the request,
     *                           or {@code null} if the request was not load balanced
     * @param readResponse       Function that reads the response from the raw
     *                           {@link NettyClientByteBodyResponse} representation. This is run exactly
     *                           once, but if there is a redirect, it potentially runs with a different
     *                           request than the original (which is why it has a request parameter)
     * @return A mono containing the response
     */
    private ExecutionFlow<HttpResponse<?>> sendRequestWithRedirects(
        PropagatedContext propagatedContext,
        AtomicReference<ScheduledExecutorService> preferredScheduler,
        @Nullable BlockHint blockHint,
        MutableHttpRequest<?> request,
        @Nullable ServiceInstance instance,
        BiFunction<MutableHttpRequest<?>, NettyClientByteBodyResponse, ? extends ExecutionFlow<? extends HttpResponse<?>>> readResponse
    ) {
        if (informationalServiceId != null && BasicHttpAttributes.getServiceId(request).isEmpty()) {
            ClientAttributes.setServiceId(request, informationalServiceId);
        }

        List<GenericHttpFilter> filters =
            filterResolver.resolveFilters(request, clientFilterEntries);

        FilterRunner.sortReverse(filters);

        FilterRunner runner = new FilterRunner(filters) {
            @Override
            protected ExecutionFlow<HttpResponse<?>> provideResponse(io.micronaut.http.HttpRequest<?> request, PropagatedContext propagatedContext) {
                try {
                    return propagatedContext.propagate(() -> sendRequestWithRedirectsNoFilter(
                        propagatedContext,
                        preferredScheduler,
                        blockHint,
                        MutableHttpRequestWrapper.wrapIfNecessary(conversionService, request),
                        instance,
                        readResponse
                    ));
                } catch (Throwable e) {
                    return ExecutionFlow.error(e);
                }
            }
        };
        return runner.run(request, propagatedContext);
    }

    private ExecutionFlow<HttpResponse<?>> sendRequestWithRedirectsNoFilter(
        PropagatedContext propagatedContext,
        AtomicReference<ScheduledExecutorService> preferredScheduler,
        @Nullable BlockHint blockHint,
        MutableHttpRequest<?> request,
        @Nullable ServiceInstance instance,
        BiFunction<MutableHttpRequest<?>, NettyClientByteBodyResponse, ? extends ExecutionFlow<? extends HttpResponse<?>>> readResponse
    ) {
        RequestKey requestKey;
        try {
            requestKey = new RequestKey(this, request.getUri());
        } catch (Exception e) {
            return ExecutionFlow.error(e);
        }

        if (!isRunning()) {
            return ExecutionFlow.error(decorate(new HttpClientException("The client is closed, unable to send request.")));
        }

        // first: connect
        return connectionManager.connect(requestKey, blockHint, preferredScheduler)
            .onErrorResume(e -> ExecutionFlow.error(failedBeforeSending(connectFailure(e), request, instance)))
            .flatMap(poolHandle -> {
                poolHandle.touch();
                preferredScheduler.set(poolHandle.channel.eventLoop());

                // build the raw request
                request.setAttribute(NettyClientHttpRequest.CHANNEL, poolHandle.channel);

                URI requestURI = request.getUri();
                boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod());
                CloseableByteBody byteBody;
                HttpHeaders outgoingHeaders = new DefaultHttpHeaders();
                try {
                    byteBody = buildNettyRequest(
                        request,
                        requestURI,
                        request
                            .getContentType()
                            .orElse(MediaType.APPLICATION_JSON_TYPE),
                        permitsBody,
                        poolHandle.channel,
                        outgoingHeaders
                    );
                } catch (Exception e) {
                    // nothing was written yet, so the connection is still usable: return it to
                    // the pool instead of leaving it marked as busy forever. Like a release after
                    // a response, this must happen on the event loop of the connection.
                    if (poolHandle.channel.eventLoop().inEventLoop()) {
                        poolHandle.release();
                    } else {
                        poolHandle.channel.eventLoop().execute(poolHandle::release);
                    }
                    return ExecutionFlow.error(e);
                }

                // send the raw request
                return sendRawRequestAllowingRetry(poolHandle, request, instance, byteBody, outgoingHeaders, blockHint, preferredScheduler);
            })
            .flatMap(byteBodyResponse -> {
                // handle redirects or map the response bytes

                int code = byteBodyResponse.code();
                HttpHeaders nettyHeaders = byteBodyResponse.getHeaders().getNettyHeaders();
                if (code > 300 && code < 400 && configuration.isFollowRedirects() && request.getAttribute(NO_FOLLOW_REDIRECTS).isEmpty() && nettyHeaders.contains(HttpHeaderNames.LOCATION)) {
                    byteBodyResponse.close();
                    String location = nettyHeaders.get(HttpHeaderNames.LOCATION);

                    MutableHttpRequest<Object> redirectRequest;
                    boolean isRedirectWithBody = code == 307 || code == 308;
                    boolean isQueryRedirect = (code == 301 || code == 302) && request.getMethod() == io.micronaut.http.HttpMethod.QUERY;
                    boolean preserveBody = isRedirectWithBody || isQueryRedirect;
                    if (preserveBody) {
                        redirectRequest = io.micronaut.http.HttpRequest.create(request.getMethod(), location);
                        request.getBody().ifPresent(redirectRequest::body);
                    } else {
                        redirectRequest = io.micronaut.http.HttpRequest.GET(location);
                    }
                    int redirectCount = request.getAttribute(REDIRECT_COUNT, Integer.class).orElse(0) + 1;
                    if (redirectCount > configuration.getMaxRedirects()) {
                        return ExecutionFlow.error(decorate(new HttpClientException("Maximum number of redirects exceeded at redirect count: " + redirectCount)));
                    }
                    redirectRequest.setAttribute(REDIRECT_COUNT, redirectCount);
                    // the per-exchange options apply to the whole exchange, redirects included
                    request.getAttribute(NO_DECOMPRESSION).ifPresent(noDecompression -> redirectRequest.setAttribute(NO_DECOMPRESSION, noDecompression));
                    return resolveRedirectURI(request, redirectRequest)
                        .flatMap(target -> {
                            setRedirectHeaders(request, redirectRequest.uri(target.uri()), preserveBody);
                            return sendRequestWithRedirects(propagatedContext, blockHint, redirectRequest.uri(target.uri()), target.instance(), readResponse);
                        });
                } else {
                    io.micronaut.http.HttpHeaders headers = byteBodyResponse.getHeaders();
                    if (log.isTraceEnabled()) {
                        log.trace("HTTP Client Response Received ({}) for Request: {} {}", byteBodyResponse.code(), request.getMethodName(), request.getUri());
                        HttpHeadersUtil.trace(log, headers.names(), headers::getAll);
                    }
                    return readResponse.apply(request, byteBodyResponse);
                }
            });
    }

    /**
     * Send the request on the given connection. If the connection was reused from the pool and
     * turns out to be closed already, an idempotent request with an available body is sent again
     * once on another connection, with the same outgoing headers. Nothing is set up for that
     * unless the request could actually be sent again.
     *
     * @param poolHandle         The connection
     * @param request            The request to send
     * @param instance           The service instance the load balancer selected, or {@code null}
     * @param byteBody           The request body
     * @param outgoingHeaders    The client-generated headers
     * @param blockHint          The optional block hint
     * @param preferredScheduler The preferred scheduler reference
     * @return The response flow
     */
    private ExecutionFlow<NettyClientByteBodyResponse> sendRawRequestAllowingRetry(
        ConnectionManager.PoolHandle poolHandle,
        MutableHttpRequest<?> request,
        @Nullable ServiceInstance instance,
        CloseableByteBody byteBody,
        HttpHeaders outgoingHeaders,
        @Nullable BlockHint blockHint,
        AtomicReference<ScheduledExecutorService> preferredScheduler
    ) {
        boolean reusedConnection = markRequestSent(poolHandle);
        if (!reusedConnection || poolHandle.http2 || !(byteBody instanceof AvailableByteBody) || !request.getMethod().isIdempotent()) {
            return sendRawRequest(poolHandle, request, instance, byteBody, outgoingHeaders, false);
        }
        return sendRawRequestWithRetry(poolHandle, request, instance, byteBody, outgoingHeaders, blockHint, preferredScheduler);
    }

    private ExecutionFlow<NettyClientByteBodyResponse> sendRawRequestWithRetry(
        ConnectionManager.PoolHandle poolHandle,
        MutableHttpRequest<?> request,
        @Nullable ServiceInstance instance,
        CloseableByteBody byteBody,
        HttpHeaders outgoingHeaders,
        @Nullable BlockHint blockHint,
        AtomicReference<ScheduledExecutorService> preferredScheduler
    ) {
        return sendRawRequest(poolHandle, request, instance, byteBody, outgoingHeaders, true)
            .onErrorResume(e -> {
                if (e instanceof StaleConnectionException stale) {
                    return resendOnNewConnection(blockHint, preferredScheduler, request, instance, outgoingHeaders, stale.replayBody);
                }
                return ExecutionFlow.error(e);
            });
    }

    /**
     * Send a request a second time, after its first attempt failed because the reused connection
     * it was written to had already been closed by the server (see
     * {@link StaleConnectionException}). The connection is acquired from the pool as usual, and
     * this attempt is not retried again.
     *
     * @param blockHint          The optional block hint
     * @param preferredScheduler The preferred scheduler reference
     * @param request            The request to send
     * @param instance           The service instance the load balancer selected, or {@code null}
     * @param outgoingHeaders    The client-generated headers of the first attempt
     * @param firstAttemptBody   The request body of the first attempt, or {@code null} if it was
     *                           empty
     * @return The response flow
     */
    private ExecutionFlow<NettyClientByteBodyResponse> resendOnNewConnection(
        @Nullable BlockHint blockHint,
        AtomicReference<ScheduledExecutorService> preferredScheduler,
        MutableHttpRequest<?> request,
        @Nullable ServiceInstance instance,
        HttpHeaders outgoingHeaders,
        @Nullable CloseableAvailableByteBody firstAttemptBody
    ) {
        if (log.isDebugEnabled()) {
            log.debug("Reused connection was closed before a response to {} {} was received, retrying once", request.getMethodName(), request.getUri());
        }
        CloseableAvailableByteBody replayBody = firstAttemptBody == null ? NettyByteBodyFactory.empty() : firstAttemptBody;
        RequestKey requestKey;
        try {
            // the first attempt was sent to this URI already, so this does not fail in practice
            requestKey = new RequestKey(this, request.getUri());
        } catch (Exception e) {
            replayBody.close();
            return ExecutionFlow.error(e);
        }
        // The replay body is owned by this method until it is either handed to sendRawRequest
        // or closed. Whoever sets this flag first (connection acquired, acquisition failed, or
        // exchange cancelled while the acquisition is pending) is responsible for the body.
        AtomicBoolean replayBodyClaimed = new AtomicBoolean();
        ExecutionFlow<NettyClientByteBodyResponse> response = connectionManager.connect(requestKey, blockHint, preferredScheduler)
            .onErrorResume(e -> {
                if (replayBodyClaimed.compareAndSet(false, true)) {
                    replayBody.close();
                }
                return ExecutionFlow.error(failedBeforeSending(connectFailure(e), request, instance));
            })
            .flatMap(poolHandle -> {
                if (!replayBodyClaimed.compareAndSet(false, true)) {
                    // the exchange was cancelled while the connection was acquired, and the
                    // body is already closed. This may run on any thread, but like any other
                    // release, this must happen on the event loop of the connection.
                    if (poolHandle.channel.eventLoop().inEventLoop()) {
                        poolHandle.release();
                    } else {
                        poolHandle.channel.eventLoop().execute(poolHandle::release);
                    }
                    return ExecutionFlow.error(new HttpClientException("Request cancelled"));
                }
                poolHandle.touch();
                preferredScheduler.set(poolHandle.channel.eventLoop());
                request.setAttribute(NettyClientHttpRequest.CHANNEL, poolHandle.channel);
                markRequestSent(poolHandle);
                return sendRawRequest(poolHandle, request, instance, replayBody, outgoingHeaders, false);
            });
        if (replayBodyClaimed.get()) {
            // the connection was available immediately, the body has been handed off already
            return response;
        }
        // cancelling the exchange cancels the pending acquisition, which then completes neither
        // way, so the body must be released on cancellation
        DelayedExecutionFlow<NettyClientByteBodyResponse> result = DelayedExecutionFlow.create();
        response.onComplete((r, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
            } else if (result.isCancelled()) {
                if (r != null) {
                    r.close();
                }
            } else {
                result.complete(r);
            }
        });
        result.onCancel(() -> {
            if (replayBodyClaimed.compareAndSet(false, true)) {
                replayBody.close();
            }
            response.cancel();
        });
        return result;
    }

    /**
     * Record that a request is sent on the given connection.
     *
     * @param poolHandle The connection
     * @return {@code true} iff an earlier request was sent on this connection, i.e. it was reused
     * from the pool
     */
    private static boolean markRequestSent(ConnectionManager.PoolHandle poolHandle) {
        return poolHandle.channel.attr(REQUEST_SENT).getAndSet(Boolean.TRUE) != null;
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

    /**
     * This is the low-level request method, without redirect handling and with raw body bytes.
     *
     * @param poolHandle The pool handle to send the request on
     * @param request    The request to send
     * @param instance   The service instance the load balancer selected, or {@code null}
     * @param byteBody   The request body
     * @param outgoingHeaders Headers to set on the outgoing netty request only
     * @param allowRetry Whether the request may fail with a {@link StaleConnectionException} so
     *                   that it is sent again on another connection. Only for idempotent requests
     *                   with an available body on a reused HTTP/1 connection
     * @return A mono containing the response
     */
    private ExecutionFlow<NettyClientByteBodyResponse> sendRawRequest(
        ConnectionManager.PoolHandle poolHandle,
        io.micronaut.http.HttpRequest<?> request,
        @Nullable ServiceInstance instance,
        CloseableByteBody byteBody,
        HttpHeaders outgoingHeaders,
        boolean allowRetry
    ) {
        poolHandle.touch();
        URI uri = request.getUri();
        String uriWithoutHost = uri.getRawPath();
        if (uri.getRawQuery() != null) {
            uriWithoutHost += "?" + uri.getRawQuery();
        }
        HttpRequest requestWithoutBody = NettyHttpRequestBuilder.asBuilder(request)
            .toHttpRequestWithoutBody();
        // the netty request may share its headers with the caller's request, so copy them before
        // adding transport headers (Host, Content-Length, Connection...) to keep the caller's
        // request unchanged and reusable
        HttpRequest nettyRequest = new DefaultHttpRequest(
            requestWithoutBody.protocolVersion(),
            requestWithoutBody.method(),
            uriWithoutHost,
            requestWithoutBody.headers().copy()
        );
        nettyRequest.headers().setAll(outgoingHeaders);

        DelayedExecutionFlow<NettyClientByteBodyResponse> flow = DelayedExecutionFlow.create();
        // need to run the create() on the event loop so that pipeline modification happens synchronously
        if (poolHandle.channel.eventLoop().inEventLoop()) {
            sendRawRequest0(poolHandle, request, instance, byteBody, flow, nettyRequest, allowRetry);
        } else {
            poolHandle.channel.eventLoop().execute(() -> sendRawRequest0(poolHandle, request, instance, byteBody, flow, nettyRequest, allowRetry));
        }
        return flow;
    }

    private void sendRawRequest0(ConnectionManager.PoolHandle poolHandle, io.micronaut.http.HttpRequest<?> request, @Nullable ServiceInstance instance, CloseableByteBody byteBody, DelayedExecutionFlow<NettyClientByteBodyResponse> sink, HttpRequest nettyRequest, boolean allowRetry) {
        if (log.isDebugEnabled()) {
            log.debug("Sending HTTP {} to {}", request.getMethodName(), request.getUri());
        }

        boolean expectContinue = HttpUtil.is100ContinueExpected(nettyRequest);
        ChannelPipeline pipeline = poolHandle.channel.pipeline();
        poolHandle.channel.attr(ResponseContentDecompressor.SKIP_DECOMPRESSION)
            .set(request.getAttribute(NO_DECOMPRESSION).isPresent() ? Boolean.TRUE : null);

        OptionalLong length = byteBody.expectedLength();
        // a request that expects 100-continue may already have been processed when the connection
        // fails, so it is never sent again
        boolean retry = allowRetry && !expectContinue;

        // if the body is streamed, we have a StreamWriter, otherwise we have a ByteBuf.
        StreamWriter streamWriter = null;
        ByteBuf byteBuf = null;
        // copy of the request body, kept so that the request can be sent again if the reused
        // connection turns out to be closed already. An empty body needs no copy, and streamed
        // bodies are never sent again.
        CloseableAvailableByteBody replayBody = null;
        try {
            if (byteBody instanceof AvailableByteBody available) {
                replayBody = retry && available.length() != 0 ? available.split() : null;
                byteBuf = NettyByteBodyFactory.toByteBuf(available);
            } else {
                streamWriter = new StreamWriter(new NettyByteBodyFactory(poolHandle.channel()).toStreaming(byteBody), e -> {
                    poolHandle.taint();
                    completeExceptionallySafe(sink, e);
                });
                pipeline.addLast(streamWriter);
            }
            prepareRequestPipeline(poolHandle, request, instance, sink, nettyRequest, expectContinue, length, streamWriter, byteBuf, retry, replayBody);
        } catch (Throwable t) {
            // the request was not written, but the pipeline may be half built: don't reuse the
            // connection, and make sure the pool handle is released and the caller sees the error
            poolHandle.taint();
            ChannelHandler responseHandler = pipeline.get(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE);
            if (responseHandler != null) {
                pipeline.remove(responseHandler);
            }
            if (streamWriter != null && pipeline.context(streamWriter) != null) {
                pipeline.remove(streamWriter);
            }
            if (byteBuf != null) {
                byteBuf.release();
            }
            if (replayBody != null) {
                // the response handler is gone, so nothing sends the request again
                replayBody.close();
            }
            byteBody.close();
            poolHandle.release();
            completeExceptionallySafe(sink, t);
            return;
        }

        Channel channel = poolHandle.channel();
        // taken before the head is written, on the event loop: nothing of this request has reached
        // the transport yet
        TransportWriteTracker writeTracker = TransportWriteTracker.find(channel);
        long writeMark = writeTracker == null ? 0 : writeTracker.mark();
        if (streamWriter == null) {
            if (!expectContinue) {
                // it's a bit more efficient to use a full request for HTTP/2
                channel.writeAndFlush(new DefaultFullHttpRequest(
                    nettyRequest.protocolVersion(),
                    nettyRequest.method(),
                    nettyRequest.uri(),
                    byteBuf,
                    nettyRequest.headers(),
                    EmptyHttpHeaders.INSTANCE
                ), requestWritePromise(channel, writeTracker, writeMark));
            } else {
                channel.writeAndFlush(nettyRequest, requestWritePromise(channel, writeTracker, writeMark));
            }
        } else {
            channel.writeAndFlush(nettyRequest, requestWritePromise(channel, writeTracker, writeMark));
            if (!expectContinue) {
                streamWriter.startWriting();
            }
        }
    }

    /**
     * Add the response handler to the pipeline and finalize the request headers, without writing
     * anything to the channel yet.
     */
    private void prepareRequestPipeline(
        ConnectionManager.PoolHandle poolHandle,
        io.micronaut.http.HttpRequest<?> request,
        @Nullable ServiceInstance instance,
        DelayedExecutionFlow<NettyClientByteBodyResponse> sink,
        HttpRequest nettyRequest,
        boolean expectContinue,
        OptionalLong length,
        @Nullable StreamWriter streamWriter,
        @Nullable ByteBuf byteBuf,
        boolean retry,
        @Nullable CloseableAvailableByteBody replayBody
    ) {
        ChannelPipeline pipeline = poolHandle.channel.pipeline();

        if (log.isTraceEnabled()) {
            HttpHeadersUtil.trace(log, nettyRequest.headers().names(), nettyRequest.headers()::getAll);
            if (byteBuf != null) {
                traceBody("Request", byteBuf);
            }
        }

        AtomicBoolean responded = new AtomicBoolean();

        // whether the body is still held back for a 100 Continue; only touched on the event loop.
        // The body is either sent (100 Continue, or the fallback timer) or dropped (a final
        // response arrived first), whichever happens first, and only once.
        AtomicBoolean stillExpectingContinue = new AtomicBoolean(expectContinue);
        AtomicReference<ScheduledFuture<?>> continueFallback = new AtomicReference<>();
        Runnable cancelContinueFallback = () -> {
            ScheduledFuture<?> fallback = continueFallback.getAndSet(null);
            if (fallback != null) {
                fallback.cancel(false);
            }
        };
        Runnable sendHeldBody = () -> {
            if (stillExpectingContinue.compareAndSet(true, false)) {
                cancelContinueFallback.run();
                if (streamWriter == null) {
                    poolHandle.channel().writeAndFlush(new DefaultLastHttpContent(byteBuf), poolHandle.channel().voidPromise());
                } else {
                    streamWriter.startWriting();
                }
            }
        };
        Runnable dropHeldBody = () -> {
            if (stillExpectingContinue.compareAndSet(true, false)) {
                cancelContinueFallback.run();
                if (streamWriter != null) {
                    streamWriter.cancel();
                } else if (byteBuf != null) {
                    byteBuf.release();
                }
                // the request was not sent completely, so the connection cannot be reused
                poolHandle.taint();
            }
        };

        // the retry state is assigned below instead of in field initializers, so that the
        // listener does not also capture the parameters
        var listener = new Http1ResponseHandler.ResponseListener() {
            /**
             * The outcome of the exchange is reported to the load balancer once: a failure
             * before the response or of its body, or else the status once the body ended or
             * the caller let it go. An exchange cancelled before its response is not reported.
             * All on the event loop.
             */
            boolean reported;
            int code;
            // whether the request can still be sent again if the connection fails, i.e. no
            // response was received yet
            boolean retryPossible;
            // whether the request is sent again in finish()
            boolean retryPending;
            // copy of the request body for sending it again, null for an empty body
            @Nullable
            CloseableAvailableByteBody unusedReplayBody;

            private void reportOnce(LoadBalancer.@Nullable Outcome outcome) {
                if (!reported) {
                    reported = true;
                    if (outcome != null) {
                        report(instance, outcome);
                    }
                }
            }

            private void reportResponse() {
                if (responded.get()) {
                    reportOnce(code >= 500 ? LoadBalancer.Outcome.SERVER_ERROR : LoadBalancer.Outcome.SUCCESS);
                }
            }

            @Override
            public void fail(ChannelHandlerContext ctx, Throwable cause) {
                poolHandle.taint();
                if (retryPossible) {
                    retryPossible = false;
                    if (!sink.isCancelled() && isConnectionClosedError(cause)) {
                        // the server closed the connection while it was idle in the pool, so it
                        // cannot have processed this request. Send it again once the dead
                        // connection is released, in finish().
                        retryPending = true;
                        return;
                    }
                    closeReplayBody();
                }
                if (!sink.isCancelled()) {
                    // nobody takes the error of a cancelled exchange, e.g. its closed connection
                    HttpClientException failure = handleResponseError(request, instance, cause);
                    reportOnce(failureOutcome(failure));
                    completeExceptionallySafe(sink, failure);
                }
            }

            @Override
            public void bodyFailed(ChannelHandlerContext ctx, Throwable cause) {
                // the body fails for its consumer, which maps and decorates the cause
                reportOnce(failureOutcome(cause));
            }

            @Override
            public void allowDiscard() {
                // the caller is done with the response before its body ended: the instance
                // responded, and what the connection does after that is not counted against it
                reportResponse();
            }

            @Override
            public void continueReceived(ChannelHandlerContext ctx) {
                sendHeldBody.run();
            }

            @Override
            public void complete(io.netty.handler.codec.http.HttpResponse response, CloseableByteBody body) {
                // the final response arrived before 100 Continue, e.g. 417 Expectation Failed: the
                // server rejected the body, so it is not sent when the fallback timer fires later,
                // while the response body is still streaming
                dropHeldBody.run();
                code = response.status().code();
                responded.set(true);
                if (retryPossible) {
                    retryPossible = false;
                    closeReplayBody();
                }
                if (!HttpUtil.isKeepAlive(response)) {
                    poolHandle.taint();
                }
                if (sink.isCancelled()) {
                    // nobody takes the response of a cancelled exchange, and its outcome says
                    // nothing about the instance
                    reported = true;
                    body.close();
                    return;
                }
                sink.complete(new NettyClientByteBodyResponse(response, body, conversionService));
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
                return NettyHttpClient.this.sizeLimits();
            }

            @Override
            public boolean isHeadResponse() {
                return nettyRequest.method().equals(HttpMethod.HEAD);
            }

            @Override
            public void finish(ChannelHandlerContext ctx) {
                // the body ended, unless it failed, which was reported first
                reportResponse();
                ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE);
                if (streamWriter != null) {
                    if (!streamWriter.isCompleted()) {
                        // if there was an error, and we didn't fully write the request yet, the
                        // connection cannot be reused
                        poolHandle.taint();
                    }
                    ctx.pipeline().remove(streamWriter);
                }
                // the body is still held if the exchange failed before any response arrived
                dropHeldBody.run();
                poolHandle.release();
                if (retryPending) {
                    retryPending = false;
                    CloseableAvailableByteBody replay = unusedReplayBody;
                    unusedReplayBody = null;
                    if ((sink.isCancelled() || !sink.tryCompleteExceptionally(new StaleConnectionException(replay))) && replay != null) {
                        replay.close();
                    }
                }
            }

            private void closeReplayBody() {
                CloseableAvailableByteBody replay = unusedReplayBody;
                if (replay != null) {
                    unusedReplayBody = null;
                    replay.close();
                }
            }
        };
        if (retry) {
            listener.retryPossible = true;
            listener.unusedReplayBody = replayBody;
        }
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, new Http1ResponseHandler(listener));
        // cancelling the exchange before the response arrives aborts the request: the connection
        // (HTTP/1) or the stream (HTTP/2) is closed, which also stops the request body
        sink.onCancel(() -> poolHandle.channel().eventLoop().execute(() -> {
            if (!responded.get()) {
                poolHandle.taint();
                poolHandle.channel().close();
            }
        }));
        poolHandle.notifyRequestPipelineBuilt();

        HttpHeaders headers = nettyRequest.headers();
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
            if (poolHandle.canReturn()) {
                nettyRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                nettyRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
        }

        if (stillExpectingContinue.get()) {
            // a server that ignores the expectation waits for the body: send it after a while anyway (RFC 9110 10.1.1).
            // The head is written right after this, on this event loop, before the timer can fire
            configuration.getExpectContinueTimeout().ifPresent(timeout ->
                continueFallback.set(poolHandle.channel().eventLoop().schedule(sendHeldBody, timeout.toNanos(), TimeUnit.NANOSECONDS)));
        }
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
     * The failure of a connection that could not be opened: the request was never sent, so the
     * caller may send it again on another connection.
     *
     * @param error The failure of the connect, or {@code null} if the channel closed without one
     * @return The failure of the request
     */
    static UnprocessedRequestException connectError(@Nullable Throwable error) {
        UnprocessedRequestException.Reason reason = error instanceof io.netty.channel.ConnectTimeoutException
            ? UnprocessedRequestException.Reason.CONNECT_TIMEOUT
            : UnprocessedRequestException.Reason.CONNECT;
        return new UnprocessedRequestException(reason, error == null ? "Unknown connect error" : "Connect Error: " + error.getMessage(), error);
    }

    private ReadBuffer charSequenceToByteBuf(CharSequence bodyValue, MediaType requestContentType) {
        return NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).copyOf(bodyValue.toString(), requestContentType.getCharset().orElse(defaultCharset));
    }

    private String getHostHeader(URI requestURI) {
        RequestKey requestKey = new RequestKey(this, requestURI);
        StringBuilder host = new StringBuilder(requestKey.getHost());
        int port = requestKey.getPort();
        if (port > -1 && port != 80 && port != 443) {
            host.append(":").append(port);
        }
        return host.toString();
    }

    private CloseableByteBody buildFormRequest(
        MutableHttpRequest<?> request,
        HttpHeaders outgoingHeaders,
        NettyByteBodyFactory bodyFactory,
        ThrowingFunction<HttpRequest, HttpPostRequestEncoder, HttpPostRequestEncoder.ErrorDataEncoderException> buildMethod
    ) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        // this function acts like a wrapper around HttpPostRequestEncoder. HttpPostRequestEncoder
        // takes a request + form data and transforms it to a request + bytes. Because we only want
        // the bytes, we need to copy the data from the netty request into the
        // outgoing headers. This is just the Content-Type header, which sometimes gets an extra
        // boundary specifier that we need.

        // build the mock netty request (only the content-type matters)
        HttpRequest nettyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        List<AsciiString> relevantHeaders = List.of(HttpHeaderNames.CONTENT_TYPE);
        for (AsciiString header : relevantHeaders) {
            nettyRequest.headers().add(header, outgoingHeaders.contains(header) ? outgoingHeaders.getAll(header) : request.getHeaders().getAll(header));
        }

        HttpPostRequestEncoder encoder = buildMethod.apply(nettyRequest);
        HttpRequest finalized = encoder.finalizeRequest();
        // copy back the content-type
        for (AsciiString header : relevantHeaders) {
            outgoingHeaders.remove(header);
            for (String value : finalized.headers().getAll(header)) {
                outgoingHeaders.add(header, value);
            }
        }
        // return the body bytes
        if (encoder.isChunked()) {
            Flux<ByteBuf> bytes = Flux.create(em -> {
                em.onRequest(n -> {
                    try {
                        while (n-- > 0) {
                            HttpContent chunk = encoder.readChunk(ByteBufAllocator.DEFAULT);
                            if (chunk == null) {
                                assert encoder.isEndOfInput();
                                em.complete();
                                break;
                            }
                            em.next(chunk.content());
                        }
                    } catch (Exception e) {
                        em.error(e);
                    }
                });
                em.onDispose(encoder::cleanFiles);
            });
            if (blockingExecutor != null &&
                encoder.getBodyListAttributes().stream().anyMatch(d -> !(d instanceof HttpData hd) || !hd.isInMemory())) {
                // readChunk in the above code can block.
                bytes = bytes.subscribeOn(Schedulers.fromExecutor(blockingExecutor));
            }
            return bodyFactory.adapt(bytes, null, null);
        } else {
            return bodyFactory.adapt(((FullHttpRequest) finalized).content());
        }
    }

    private HttpPostRequestEncoder buildFormDataRequest(HttpRequest baseRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(baseRequest, false);

        Map<String, Object> formData;
        if (bodyValue instanceof Map) {
            //noinspection unchecked
            formData = (Map<String, Object>) bodyValue;
        } else {
            formData = BeanMap.of(bodyValue);
        }
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                if (value instanceof Collection<?> collection) {
                    for (Object val : collection) {
                        addBodyAttribute(postRequestEncoder, entry.getKey(), val);
                    }
                } else {
                    addBodyAttribute(postRequestEncoder, entry.getKey(), value);
                }
            }
        }
        return postRequestEncoder;
    }

    private void addBodyAttribute(HttpPostRequestEncoder postRequestEncoder, String key, Object value) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        Optional<String> converted = conversionService.convert(value, String.class);
        if (converted.isPresent()) {
            postRequestEncoder.addBodyAttribute(key, converted.get());
        }
    }

    private HttpPostRequestEncoder buildMultipartRequest(HttpRequest baseRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(factory, baseRequest, true, CharsetUtil.UTF_8, HttpPostRequestEncoder.EncoderMode.HTML5);
        if (bodyValue instanceof MultipartBody.Builder builder) {
            bodyValue = builder.build();
        }
        if (bodyValue instanceof MultipartBody multipartBody) {
            postRequestEncoder.setBodyHttpDatas(multipartBody.getData(new MultipartDataFactory<>() {
                @Override
                public InterfaceHttpData createFileUpload(String name, String filename, MediaType contentType, @Nullable String encoding, @Nullable Charset charset, long length) {
                    return factory.createFileUpload(
                        baseRequest,
                        name,
                        filename,
                        contentType.toString(),
                        encoding,
                        charset,
                        length
                    );
                }

                @Override
                public InterfaceHttpData createAttribute(String name, String value) {
                    return factory.createAttribute(
                        baseRequest,
                        name,
                        value
                    );
                }

                @Override
                public void setContent(InterfaceHttpData fileUploadObject, Object content) throws IOException {
                    if (fileUploadObject instanceof FileUpload fu) {
                        if (content instanceof InputStream stream) {
                            fu.setContent(stream);
                        } else if (content instanceof File file) {
                            fu.setContent(file);
                        } else if (content instanceof byte[] bytes) {
                            final ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
                            fu.setContent(buffer);
                        }
                    }
                }
            }));
        } else {
            throw new MultipartException("The type %s is not a supported type for a multipart request body".formatted(bodyValue.getClass().getName()));
        }

        return postRequestEncoder;
    }

    private void traceBody(String type, ByteBuf content) {
        log.trace("{} Body", type);
        log.trace("----");
        log.trace(content.toString(defaultCharset));
        log.trace("----");
    }

    private void traceChunk(ByteBuf content) {
        log.trace("Sending Chunk");
        log.trace("----");
        log.trace(content.toString(defaultCharset));
        log.trace("----");
    }

    private static MediaTypeCodecRegistry createDefaultMediaTypeRegistry() {
        JsonMapper mapper = JsonMapper.createDefault();
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        return MediaTypeCodecRegistry.of(
            new JsonMediaTypeCodec(mapper, configuration, null),
            new JsonStreamMediaTypeCodec(mapper, configuration, null)
        );
    }

    private static MessageBodyHandlerRegistry createDefaultMessageBodyHandlerRegistry() {
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        ContextlessMessageBodyHandlerRegistry registry = new ContextlessMessageBodyHandlerRegistry(
            applicationConfiguration,
            NettyByteBufferFactory.DEFAULT,
            new NettyByteBufMessageBodyHandler(),
            new WritableBodyWriter(applicationConfiguration)
        );
        JsonMapper mapper = JsonMapper.createDefault();
        registry.add(MediaType.APPLICATION_JSON_TYPE, new NettyJsonHandler<>(mapper));
        registry.add(MediaType.APPLICATION_JSON_TYPE, new CharSequenceBodyWriter(StandardCharsets.UTF_8));
        registry.add(MediaType.APPLICATION_JSON_STREAM_TYPE, new NettyJsonStreamHandler<>(mapper));
        return registry;
    }

    static boolean isSecureScheme(String scheme) {
        // fast path
        if (scheme.equals("http")) {
            return false;
        }
        if (scheme.equals("https")) {
            return true;
        }
        // actual case-insensitive check
        return io.micronaut.http.HttpRequest.SCHEME_HTTPS.equalsIgnoreCase(scheme) || SCHEME_WSS.equalsIgnoreCase(scheme);
    }

    private <E extends HttpClientException> E decorate(E exc) {
        return HttpClientExceptionUtils.populateServiceId(exc, informationalServiceId, configuration);
    }

    /**
     * Map a failure of a response, before or after its headers, to a client exception. The
     * outcome for the load balancer is not reported here: the response listener of the exchange
     * reports it, see {@link #failureOutcome}.
     *
     * @param finalRequest The request
     * @param instance     The service instance the load balancer selected, or {@code null}
     * @param cause        The failure
     * @return The client exception
     */
    private HttpClientException handleResponseError(io.micronaut.http.HttpRequest<?> finalRequest, @Nullable ServiceInstance instance, Throwable cause) {
        String message = cause.getMessage();
        if (message == null) {
            message = cause.getClass().getSimpleName();
        }
        if (log.isTraceEnabled()) {
            log.trace("HTTP Client exception ({}) occurred for request : {} {}",
                message, finalRequest.getMethodName(), finalRequest.getUri());
        }

        HttpClientException result;
        if (cause instanceof io.micronaut.http.exceptions.ContentLengthExceededException clee) {
            result = decorate(new ContentLengthExceededException(Objects.requireNonNull(clee.getMessage(), "Content length exceeded")));
        } else if (cause instanceof BufferLengthExceededException blee) {
            result = decorate(new ContentLengthExceededException(blee.getAdvertisedLength(), blee.getReceivedLength()));
        } else if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
            result = ReadTimeoutException.TIMEOUT_EXCEPTION;
        } else if (cause instanceof HttpClientException hce) {
            result = decorate(hce);
        } else {
            result = decorate(new HttpClientException("Error occurred reading HTTP response: " + message, cause));
        }
        if (result instanceof UnprocessedRequestException unprocessed) {
            unprocessed.setTarget(finalRequest.getUri(), instance);
            if (unprocessed.getServiceId() == null) {
                decorate(unprocessed);
            }
        }
        return result;
    }

    /**
     * Classify a failure to get a connection for a request: a connection pool acquire timeout
     * fails the flow with a plain {@link TimeoutException}, but no byte of the request was sent.
     *
     * @param failure The failure
     * @return The failure, an {@link UnprocessedRequestException} for an acquire timeout
     */
    private Throwable connectFailure(Throwable failure) {
        if (failure instanceof TimeoutException) {
            return new UnprocessedRequestException(UnprocessedRequestException.Reason.POOL_ACQUIRE,
                "Cannot acquire connection: the acquire timeout of " + configuration.getConnectionPoolConfiguration().getAcquireTimeout().orElse(null) + " elapsed", failure);
        }
        return failure;
    }

    /**
     * The outcome to report to the load balancer for a failed exchange.
     *
     * @param failure The failure, before the response or of its body, as raised or as mapped
     * @return The outcome, or {@code null} if the failure says nothing about the instance
     */
    private static LoadBalancer.@Nullable Outcome failureOutcome(Throwable failure) {
        if (failure instanceof UnprocessedRequestException unprocessed) {
            return switch (unprocessed.getReason()) {
                case CONNECT, CONNECT_TIMEOUT -> LoadBalancer.Outcome.CONNECT_FAILURE;
                case STREAM_REFUSED -> LoadBalancer.Outcome.RESET;
                // a pool that is full, or a keep-alive connection the server closed, say nothing about the instance
                default -> null;
            };
        } else if (failure instanceof ReadTimeoutException || failure instanceof io.netty.handler.timeout.ReadTimeoutException) {
            return LoadBalancer.Outcome.TIMEOUT;
        } else if (failure instanceof ResponseClosedException || failure instanceof StreamResetException) {
            return LoadBalancer.Outcome.RESET;
        }
        return null;
    }

    /**
     * Record the target of a request that was not sent on its exception, and the service id if
     * it has none yet.
     *
     * @param failure  The failure
     * @param request  The request that failed
     * @param instance The service instance the load balancer selected, or {@code null}
     * @return The failure
     */
    private Throwable failedBeforeSending(Throwable failure, io.micronaut.http.HttpRequest<?> request, @Nullable ServiceInstance instance) {
        if (failure instanceof UnprocessedRequestException unprocessed) {
            unprocessed.setTarget(request.getUri(), instance);
            if (unprocessed.getServiceId() == null) {
                decorate(unprocessed);
            }
            LoadBalancer.Outcome outcome = failureOutcome(unprocessed);
            if (outcome != null) {
                report(instance, outcome);
            }
        }
        return failure;
    }

    /**
     * Report the outcome of an exchange to the load balancer that selected its instance, if any.
     *
     * @param instance The service instance the load balancer selected, or {@code null}
     * @param outcome  The outcome
     */
    private void report(@Nullable ServiceInstance instance, LoadBalancer.Outcome outcome) {
        if (instance != null && loadBalancer != null) {
            loadBalancer.report(instance, outcome);
        }
    }

    private void setRedirectHeaders(io.micronaut.http.HttpRequest<?> request,
                                    MutableHttpRequest<Object> redirectRequest,
                                    boolean preserveBody) {
        if (request == null) {
            return;
        }
        boolean sameOrigin;
        try {
            sameOrigin = new RequestKey(this, request.getUri())
                .equals(new RequestKey(this, redirectRequest.getUri()));
        } catch (Exception e) {
            // fallback
            sameOrigin = false;
        }
        DefaultHttpHeaders headersToBlock = resolveRedirectFilteredHeaders(!sameOrigin, preserveBody);
        for (Map.Entry<String, List<String>> originalHeader : request.getHeaders()) {
            String headerName = originalHeader.getKey();
            if (headersToBlock.contains(headerName)) {
                continue;
            }
            final List<String> originalHeaderValue = originalHeader.getValue();
            if (originalHeaderValue != null && !originalHeaderValue.isEmpty()) {
                for (String value : originalHeaderValue) {
                    if (value != null) {
                        redirectRequest.header(headerName, value);
                    }
                }
            }
        }
    }

    private DefaultHttpHeaders resolveRedirectFilteredHeaders(boolean crossOrigin, boolean preserveBody) {
        if (crossOrigin) {
            return preserveBody ? redirectCrossOriginPreserveBodyHeaders.get() : redirectCrossOriginNonPreserveBodyHeaders.get();
        }
        return preserveBody ? redirectSameOriginPreserveBodyHeaders.get() : redirectSameOriginNonPreserveBodyHeaders.get();
    }

    private DefaultHttpHeaders buildRedirectFilteredHeaders(boolean crossOrigin, boolean preserveBody) {
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        for (String header : configuration.getRedirectAlwaysFilteredHeaders()) {
            headers.add(header, "");
        }
        if (crossOrigin) {
            for (String header : configuration.getRedirectCrossOriginFilteredHeaders()) {
                headers.add(header, "");
            }
        }
        if (!preserveBody) {
            for (String header : configuration.getRedirectAdditionalNonPreserveBodyFilteredHeaders()) {
                headers.add(header, "");
            }
        }
        return headers;
    }

    private BodySizeLimits sizeLimits() {
        return new BodySizeLimits(Long.MAX_VALUE, configuration.getMaxContentLength());
    }

    private static <O, E> boolean shouldConvertWithBodyType(io.netty.handler.codec.http.HttpResponse msg,
                                                            HttpClientConfiguration configuration,
                                                            @Nullable Argument<O> bodyType,
                                                            Argument<E> errorType) {
        if (msg.status().code() < 400) {
            return true;
        }
        return !configuration.isExceptionOnErrorStatus() && bodyType != null && bodyType.equalsType(errorType);
    }

    /**
     * Create a {@link HttpClientResponseException} if parsing of the HTTP error body failed.
     */
    private HttpClientResponseException makeErrorBodyParseError(FullHttpResponse fullResponse, Throwable t) {
        FullNettyClientHttpResponse<Object> errorResponse = new FullNettyClientHttpResponse<>(
            fullResponse,
            handlerRegistry,
            null,
            false,
            conversionService
        );
        return decorate(new HttpClientResponseException(
            "Error decoding HTTP error response body: " + t.getMessage(),
            t,
            errorResponse,
            null
        ));
    }

    /**
     * Create a {@link HttpClientResponseException} from a response with a failed HTTP status.
     */
    private HttpClientResponseException makeErrorFromRequestBody(@Nullable Argument<?> errorType, HttpResponseStatus status, FullNettyClientHttpResponse<?> response) {
        if (errorType != null && errorType != HttpClient.DEFAULT_ERROR_TYPE) {
            return decorate(new HttpClientResponseException(
                status.reasonPhrase(),
                null,
                response,
                new HttpClientErrorDecoder() {
                    @Override
                    public Argument<?> getErrorType(MediaType mediaType) {
                        return errorType;
                    }
                }
            ));
        } else {
            return decorate(new HttpClientResponseException(status.reasonPhrase(), response));
        }
    }

    private static boolean hasBody(HttpResponse<?> response) {
        if (response.code() >= HttpStatus.CONTINUE.getCode() && response.code() < HttpStatus.OK.getCode()) {
            return false;
        }

        if (response.code() == HttpResponseStatus.NO_CONTENT.code() ||
            response.code() == HttpResponseStatus.NOT_MODIFIED.code()) {
            return false;
        }

        OptionalLong contentLength = response.getHeaders().contentLength();
        return contentLength.isEmpty() || contentLength.getAsLong() != 0;
    }

    /**
     * Key used for connection pooling and determining host/port.
     */
    public static class RequestKey {
        private final String host;
        private final int port;
        private final boolean secure;

        /**
         * @param ctx        The HTTP client that created this request key. Only used for exception
         *                   context, not stored
         * @param requestURI The request URI
         */
        public RequestKey(NettyHttpClient ctx, URI requestURI) {
            this.secure = isSecureScheme(requestURI.getScheme());
            String host = requestURI.getHost();
            int port;
            if (host == null) {
                host = requestURI.getAuthority();
                if (host == null) {
                    throw decorate(ctx, new NoHostException("URI specifies no host to connect to"));
                }

                final int i = host.indexOf(':');
                if (i > -1) {
                    final String portStr = host.substring(i + 1);
                    host = host.substring(0, i);
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        throw decorate(ctx, new HttpClientException("URI specifies an invalid port: " + portStr));
                    }
                } else {
                    port = requestURI.getPort() > -1 ? requestURI.getPort() : secure ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
                }
            } else {
                port = requestURI.getPort() > -1 ? requestURI.getPort() : secure ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
            }

            this.host = host;
            this.port = port;
        }

        public InetSocketAddress getRemoteAddress() {
            return InetSocketAddress.createUnresolved(host, port);
        }

        public boolean isSecure() {
            return secure;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof RequestKey that)) {
                return false;
            }
            return port == that.port &&
                secure == that.secure &&
                Objects.equals(host, that.host);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(host, port, secure);
        }

        private <E extends HttpClientException> E decorate(NettyHttpClient ctx, E exc) {
            return HttpClientExceptionUtils.populateServiceId(exc, ctx.informationalServiceId, ctx.configuration);
        }
    }

    /**
     * Used as a holder for the current SSE event.
     */
    private static final class CurrentEvent {
        byte[] data = new byte[0];
        @Nullable
        String id;
        @Nullable
        String name;
        @Nullable
        Duration retry;
    }

    /**
     * The absolute URI a request is sent to, and the service instance the load balancer selected
     * for it, if the request was load balanced.
     *
     * @param uri      The absolute request URI
     * @param instance The selected instance, or {@code null} if the request URI was absolute
     */
    record ResolvedTarget(URI uri, @Nullable ServiceInstance instance) {
    }

    /**
     * Internal signal that a request failed because the reused connection it was written to had
     * already been closed, before any part of the response was received. The request is sent
     * again on another connection, and this exception never reaches the caller.
     */
    private static final class StaleConnectionException extends RuntimeException {
        @Nullable
        final transient CloseableAvailableByteBody replayBody;

        StaleConnectionException(@Nullable CloseableAvailableByteBody replayBody) {
            super("Reused connection was closed before the response was received", null, false, false);
            this.replayBody = replayBody;
        }
    }

    /**
     * Notified whenever a client is started or stopped, so that the owner of the client can
     * track the clients that are running.
     */
    interface LifecycleListener {
        /**
         * Called after {@link #start()}.
         *
         * @param client The client
         */
        void onStart(NettyHttpClient client);

        /**
         * Called after {@link #stop()}, including when the client is closed.
         *
         * @param client The client
         */
        void onStop(NettyHttpClient client);
    }
}
