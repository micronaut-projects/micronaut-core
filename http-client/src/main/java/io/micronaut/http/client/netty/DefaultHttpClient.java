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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.ConversionServiceAware;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.functional.ThrowingFunction;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.ContextlessMessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientErrorDecoder;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientExceptionUtils;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.filters.ClientServerContextFilter;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.multipart.MultipartDataFactory;
import io.micronaut.http.client.netty.ssl.ClientSslBuilder;
import io.micronaut.http.client.netty.ssl.NettyClientSslBuilder;
import io.micronaut.http.client.netty.websocket.NettyWebSocketClientHandler;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ContextPathUtils;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.filter.FilterOrder;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.body.AvailableNettyByteBody;
import io.micronaut.http.netty.body.BufferConsumer;
import io.micronaut.http.netty.body.NettyBodyAdapter;
import io.micronaut.http.netty.body.NettyByteBody;
import io.micronaut.http.netty.body.NettyByteBufMessageBodyHandler;
import io.micronaut.http.netty.body.NettyCharSequenceBodyWriter;
import io.micronaut.http.netty.body.NettyJsonHandler;
import io.micronaut.http.netty.body.NettyJsonStreamHandler;
import io.micronaut.http.netty.body.NettyWritableBodyWriter;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
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
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
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
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Default implementation of the {@link HttpClient} interface based on Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultHttpClient implements
        WebSocketClient,
        HttpClient,
        StreamingHttpClient,
        SseClient,
        ProxyHttpClient,
        Closeable,
        AutoCloseable {

    /**
     * Default logger, use {@link #log} where possible.
     */
    private static final Logger DEFAULT_LOG = LoggerFactory.getLogger(DefaultHttpClient.class);
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    /**
     * Which headers <i>not</i> to copy from the first request when redirecting to a second request. There doesn't
     * appear to be a spec for this. {@link HttpURLConnection} seems to drop all headers, but that would be a
     * breaking change.
     * <p>
     * Stored as a {@link HttpHeaders} with empty values because presumably someone thought about optimizing those
     * already.
     */
    private static final HttpHeaders REDIRECT_HEADER_BLOCKLIST;

    static {
        REDIRECT_HEADER_BLOCKLIST = new DefaultHttpHeaders();
        // The host should be recalculated based on the location
        REDIRECT_HEADER_BLOCKLIST.add(HttpHeaderNames.HOST, "");
        // post body headers
        REDIRECT_HEADER_BLOCKLIST.add(HttpHeaderNames.CONTENT_TYPE, "");
        REDIRECT_HEADER_BLOCKLIST.add(HttpHeaderNames.CONTENT_LENGTH, "");
        REDIRECT_HEADER_BLOCKLIST.add(HttpHeaderNames.TRANSFER_ENCODING, "");
        REDIRECT_HEADER_BLOCKLIST.add(HttpHeaderNames.CONNECTION, "");
    }

    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;
    protected ByteBufferFactory<ByteBufAllocator, ByteBuf> byteBufferFactory = new NettyByteBufferFactory();

    ConnectionManager connectionManager;

    private MessageBodyHandlerRegistry handlerRegistry;
    private final List<HttpFilterResolver.FilterEntry> clientFilterEntries;
    private final LoadBalancer loadBalancer;
    private final HttpClientConfiguration configuration;
    private final String contextPath;
    private final Charset defaultCharset;
    private final Logger log;
    private final HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver;
    private final WebSocketBeanRegistry webSocketRegistry;
    private final RequestBinderRegistry requestBinderRegistry;
    private final String informationalServiceId;
    private final ConversionService conversionService;
    @Nullable
    private final ExecutorService blockingExecutor;

    /**
     * Construct a client for the given arguments.
     *
     * @param loadBalancer                    The {@link LoadBalancer} to use for selecting servers
     * @param configuration                   The {@link HttpClientConfiguration} object
     * @param contextPath                     The base URI to prepend to request uris
     * @param threadFactory                   The thread factory to use for client threads
     * @param nettyClientSslBuilder           The SSL builder
     * @param codecRegistry                   The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param handlerRegistry                 The handler registry for encoding and decoding
     * @param annotationMetadataResolver      The annotation metadata resolver
     * @param conversionService               The conversion service
     * @param filters                         The filters to use
     * @deprecated Please go through the {@link #builder()} instead. If you need access to properties that are not public in the builder, make them public in core and document their usage.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer,
                             @NonNull HttpClientConfiguration configuration,
                             @Nullable String contextPath,
                             @Nullable ThreadFactory threadFactory,
                             ClientSslBuilder nettyClientSslBuilder,
                             @NonNull MediaTypeCodecRegistry codecRegistry,
                             @NonNull MessageBodyHandlerRegistry handlerRegistry,
                             @Nullable AnnotationMetadataResolver annotationMetadataResolver,
                             ConversionService conversionService,
                             HttpClientFilter... filters) {
        this(
            builder()
                .loadBalancer(loadBalancer)
                .configuration(configuration)
                .contextPath(contextPath)
                .threadFactory(threadFactory)
                .nettyClientSslBuilder(nettyClientSslBuilder)
                .codecRegistry(codecRegistry)
                .handlerRegistry(handlerRegistry)
                .conversionService(conversionService)
                .annotationMetadataResolver(annotationMetadataResolver)
                .filters(filters)
        );
    }

    /**
     * Construct a client for the given arguments.
     * @param loadBalancer                    The {@link LoadBalancer} to use for selecting servers
     * @param explicitHttpVersion                     The HTTP version to use. Can be null and defaults to {@link io.micronaut.http.HttpVersion#HTTP_1_1}
     * @param configuration                   The {@link HttpClientConfiguration} object
     * @param contextPath                     The base URI to prepend to request uris
     * @param filterResolver                  The http client filter resolver
     * @param clientFilterEntries             The client filter entries
     * @param threadFactory                   The thread factory to use for client threads
     * @param nettyClientSslBuilder           The SSL builder
     * @param codecRegistry                   The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param handlerRegistry                 The handler registry for encoding and decoding
     * @param webSocketBeanRegistry           The websocket bean registry
     * @param requestBinderRegistry           The request binder registry
     * @param eventLoopGroup                  The event loop group to use
     * @param socketChannelFactory            The socket channel factory
     * @param udpChannelFactory               The UDP channel factory
     * @param clientCustomizer                The pipeline customizer
     * @param informationalServiceId          Optional service ID that will be passed to exceptions created by this client
     * @param conversionService               The conversion service
     * @param resolverGroup                   Optional predefined resolver group
     * @deprecated Please go through the {@link #builder()} instead. If you need access to properties that are not public in the builder, make them public in core and document their usage.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer,
                             @Nullable HttpVersionSelection explicitHttpVersion,
                             @NonNull HttpClientConfiguration configuration,
                             @Nullable String contextPath,
                             @NonNull HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
                             @NonNull List<HttpFilterResolver.FilterEntry> clientFilterEntries,
                             @Nullable ThreadFactory threadFactory,
                             @NonNull ClientSslBuilder nettyClientSslBuilder,
                             @NonNull MediaTypeCodecRegistry codecRegistry,
                             @NonNull MessageBodyHandlerRegistry handlerRegistry,
                             @NonNull WebSocketBeanRegistry webSocketBeanRegistry,
                             @NonNull RequestBinderRegistry requestBinderRegistry,
                             @Nullable EventLoopGroup eventLoopGroup,
                             @NonNull ChannelFactory<? extends SocketChannel> socketChannelFactory,
                             @NonNull ChannelFactory<? extends DatagramChannel> udpChannelFactory,
                             NettyClientCustomizer clientCustomizer,
                             @Nullable String informationalServiceId,
                             ConversionService conversionService,
                             @Nullable AddressResolverGroup<?> resolverGroup
    ) {
        this(
            builder()
                .loadBalancer(loadBalancer)
                .explicitHttpVersion(explicitHttpVersion)
                .configuration(configuration)
                .contextPath(contextPath)
                .filterResolver(filterResolver)
                .clientFilterEntries(clientFilterEntries)
                .threadFactory(threadFactory)
                .nettyClientSslBuilder(nettyClientSslBuilder)
                .codecRegistry(codecRegistry)
                .handlerRegistry(handlerRegistry)
                .webSocketBeanRegistry(webSocketBeanRegistry)
                .requestBinderRegistry(requestBinderRegistry)
                .eventLoopGroup(eventLoopGroup)
                .socketChannelFactory(socketChannelFactory)
                .udpChannelFactory(udpChannelFactory)
                .clientCustomizer(clientCustomizer)
                .informationalServiceId(informationalServiceId)
                .conversionService(conversionService)
                .resolverGroup(resolverGroup)
        );
    }

    DefaultHttpClient(DefaultHttpClientBuilder builder) {
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
        this.filterResolver = builder.filterResolver;
        if (builder.clientFilterEntries != null) {
            this.clientFilterEntries = builder.clientFilterEntries;
        } else {
            this.clientFilterEntries = builder.filterResolver.resolveFilterEntries(
                    new ClientFilterResolutionContext(null, AnnotationMetadata.EMPTY_METADATA)
            );
        }
        this.webSocketRegistry = builder.webSocketBeanRegistry;
        this.conversionService = builder.conversionService;
        this.requestBinderRegistry = builder.requestBinderRegistry == null ? new DefaultRequestBinderRegistry(conversionService) : builder.requestBinderRegistry;
        this.informationalServiceId = builder.informationalServiceId;
        this.blockingExecutor = builder.blockingExecutor;

        this.connectionManager = new ConnectionManager(
            log,
            builder.eventLoopGroup,
            builder.threadFactory == null ? new DefaultThreadFactory(MultithreadEventLoopGroup.class) : builder.threadFactory,
            configuration,
            builder.explicitHttpVersion,
            builder.socketChannelFactory,
            builder.udpChannelFactory,
            builder.nettyClientSslBuilder == null ? new NettyClientSslBuilder(new ResourceResolver()) : builder.nettyClientSslBuilder,
            builder.clientCustomizer,
            builder.informationalServiceId,
            builder.resolverGroup);
    }

    /**
     * @param uri The URL
     * @deprecated Please go through the {@link #builder()} instead.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable URI uri) {
        this(builder().uri(uri));
    }

    /**
     * @deprecated Please go through the {@link #builder()} instead.
     */
    @Deprecated
    public DefaultHttpClient() {
        this(builder());
    }

    /**
     * @param uri           The URI
     * @param configuration The {@link HttpClientConfiguration} object
     * @deprecated Please go through the {@link #builder()} instead.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable URI uri, @NonNull HttpClientConfiguration configuration) {
        this(
            builder()
                .uri(uri)
                .configuration(configuration)
        );
    }

    /**
     * Constructor used by micronaut-oracle-cloud.
     *
     * @param uri           The URI
     * @param configuration The {@link HttpClientConfiguration} object
     * @param clientSslBuilder The SSL builder
     * @deprecated Please go through the {@link #builder()} instead.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable URI uri, @NonNull HttpClientConfiguration configuration, @NonNull ClientSslBuilder clientSslBuilder) {
        this(
            builder()
                .uri(uri)
                .configuration(configuration)
                .nettyClientSslBuilder(clientSslBuilder)
        );
    }

    /**
     * @param loadBalancer  The {@link LoadBalancer} to use for selecting servers
     * @param configuration The {@link HttpClientConfiguration} object
     * @deprecated Please go through the {@link #builder()} instead. If you need access to properties that are not public in the builder, make them public in core and document their usage.
     */
    @Deprecated
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer, HttpClientConfiguration configuration) {
        this(
            builder()
                .loadBalancer(loadBalancer)
                .configuration(configuration)
        );
    }

    /**
     * Create a new builder for a {@link DefaultHttpClient}.
     *
     * @return The builder
     * @since 4.7.0
     */
    @NonNull
    public static DefaultHttpClientBuilder builder() {
        return new DefaultHttpClientBuilder();
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

    @Override
    public HttpClient start() {
        if (!isRunning()) {
            connectionManager.start();
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
        return this;
    }

    /**
     * @return The {@link MediaTypeCodecRegistry} used by this client
     * @deprecated Use body handlers instead
     */
    @Deprecated
    public MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return mediaTypeCodecRegistry;
    }

    /**
     * Sets the {@link MediaTypeCodecRegistry} used by this client.
     *
     * @param mediaTypeCodecRegistry The registry to use. Should not be null
     * @deprecated Use builder instead
     */
    @Deprecated(forRemoval = true)
    public void setMediaTypeCodecRegistry(MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        if (mediaTypeCodecRegistry != null) {
            this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        }
    }

    /**
     * Get the handler registry for this client.
     *
     * @return The handler registry
     */
    @NonNull
    public final MessageBodyHandlerRegistry getHandlerRegistry() {
        return handlerRegistry;
    }

    /**
     * Set the handler registry for this client.
     *
     * @param handlerRegistry The handler registry
     * @deprecated Use builder instead
     */
    @Deprecated(forRemoval = true)
    public final void setHandlerRegistry(@NonNull MessageBodyHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return new BlockingHttpClient() {

            @Override
            public void close() {
                DefaultHttpClient.this.close();
            }

            @Override
            public <I, O, E> HttpResponse<O> exchange(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
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
                return Flux.from(DefaultHttpClient.this.exchange(request, bodyType, errorType, blockHint))
                    .blockFirst();
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
        };
    }

    @NonNull
    private <I> MutableHttpRequest<?> toMutableRequest(io.micronaut.http.HttpRequest<I> request) {
        return MutableHttpRequestWrapper.wrapIfNecessary(conversionService, request);
    }

    @SuppressWarnings("SubscriberImplementation")
    @Override
    public <I> Publisher<Event<ByteBuffer<?>>> eventStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        setupConversionService(request);
        return eventStreamOrError(request, null);
    }

    private <I> Publisher<Event<ByteBuffer<?>>> eventStreamOrError(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {

        if (request instanceof MutableHttpRequest<?> httpRequest) {
            httpRequest.accept(MediaType.TEXT_EVENT_STREAM_TYPE);
        }

        return Flux.create(emitter ->
                dataStream(request, errorType).subscribe(new Subscriber<>() {
                    private Subscription dataSubscription;
                    private CurrentEvent currentEvent;

                    @Override
                    public void onSubscribe(Subscription s) {
                        this.dataSubscription = s;
                        Disposable cancellable = () -> dataSubscription.cancel();
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
                                    Event event = Event.of(byteBufferFactory.wrap(currentEvent.data))
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
                                                if (d == null) {
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
                                dataSubscription.request(1);
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
                        dataSubscription.cancel();
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

    @Override
    public <I, B> Publisher<Event<B>> eventStream(@NonNull io.micronaut.http.HttpRequest<I> request,
                                                  @NonNull Argument<B> eventType) {
        setupConversionService(request);
        return eventStream(request, eventType, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, B> Publisher<Event<B>> eventStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<B> eventType, @NonNull Argument<?> errorType) {
        setupConversionService(request);
        MessageBodyReader<B> reader = handlerRegistry.getReader(eventType, List.of(MediaType.APPLICATION_JSON_TYPE));
        return Flux.from(eventStreamOrError(request, errorType)).map(byteBufferEvent -> {
            ByteBuffer<?> data = byteBufferEvent.getData();

            B decoded = reader.read(eventType, MediaType.APPLICATION_JSON_TYPE, request.getHeaders(), data);
            return Event.of(byteBufferEvent, decoded);
        });
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        setupConversionService(request);
        return dataStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {
        setupConversionService(request);
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return new MicronautFlux<>(Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> dataStreamImpl(toMutableRequest(request), errorType, parentRequest, requestURI)))
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
    public <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return exchangeStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {
        setupConversionService(request);
        io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return new MicronautFlux<>(Flux.from(resolveRequestURI(request))
                .flatMap(uri -> exchangeStreamImpl(parentRequest, toMutableRequest(request), errorType, uri)))
                .doAfterNext(byteBufferHttpResponse -> {
                    ByteBuffer<?> buffer = byteBufferHttpResponse.body();
                    if (buffer instanceof ReferenceCounted counted) {
                        counted.release();
                    }
                });
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> type) {
        return jsonStream(request, type, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> type, @NonNull Argument<?> errorType) {
        setupConversionService(request);
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        setupConversionService(parentRequest);
        return Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> jsonStreamImpl(parentRequest, toMutableRequest(request), type, errorType, requestURI));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I> Publisher<Map<String, Object>> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return (Publisher) jsonStream(request, Map.class);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Class<O> type) {
        setupConversionService(request);
        return jsonStream(request, Argument.of(type));
    }

    @Override
    public <I, O, E> Publisher<HttpResponse<O>> exchange(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> bodyType, @NonNull Argument<E> errorType) {
        return exchange(request, bodyType, errorType, null);
    }

    @NonNull
    private <I, O, E> Flux<HttpResponse<O>> exchange(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType, @Nullable BlockHint blockHint) {
        setupConversionService(request);
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        Publisher<URI> uriPublisher = resolveRequestURI(request);
        return Flux.from(uriPublisher)
            .switchMap(uri -> (Publisher) exchangeImpl(uri, parentRequest, toMutableRequest(request), bodyType, errorType, blockHint));
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
        Publisher<URI> uriPublisher = resolveRequestURI(request);
        return Flux.from(uriPublisher)
                .switchMap(resolvedURI -> connectWebSocket(resolvedURI, request, clientEndpointType, null));
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters) {
        WebSocketBean<T> webSocketBean = webSocketRegistry.getWebSocket(clientEndpointType);
        String uri = webSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class).orElse("/ws");
        uri = UriTemplate.of(uri).expand(parameters);
        MutableHttpRequest<Object> request = io.micronaut.http.HttpRequest.GET(uri);
        Publisher<URI> uriPublisher = resolveRequestURI(request);

        return Flux.from(uriPublisher)
                .switchMap(resolvedURI -> connectWebSocket(resolvedURI, request, clientEndpointType, webSocketBean));

    }

    @Override
    public void close() {
        stop();
    }

    private <T> Publisher<T> connectWebSocket(URI uri, MutableHttpRequest<?> request, Class<T> clientEndpointType, WebSocketBean<T> webSocketBean) {
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

        return connectionManager.connectForWebsocket(requestKey, handler)
            .then(handler.getHandshakeCompletedMono());
    }

    private <I> Flux<HttpResponse<ByteBuffer<?>>> exchangeStreamImpl(io.micronaut.http.HttpRequest<Object> parentRequest, MutableHttpRequest<I> request, Argument<?> errorType, URI requestURI) {
        Flux<HttpResponse<?>> streamResponsePublisher = Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
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

    private <I, O> Flux<O> jsonStreamImpl(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<I> request, Argument<O> type, Argument<?> errorType, URI requestURI) {
        Flux<HttpResponse<?>> streamResponsePublisher =
                Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
        return streamResponsePublisher.switchMap(response -> {
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

    private <I> Flux<ByteBuffer<?>> dataStreamImpl(MutableHttpRequest<I> request, Argument<?> errorType, io.micronaut.http.HttpRequest<Object> parentRequest, URI requestURI) {
        Flux<HttpResponse<?>> streamResponsePublisher = Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
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
    private <I> Publisher<HttpResponse<?>> buildStreamExchange(
            @Nullable io.micronaut.http.HttpRequest<?> parentRequest,
            @NonNull MutableHttpRequest<I> request,
            @NonNull URI requestURI,
            @Nullable Argument<?> errorType) {

        AtomicReference<MutableHttpRequest<?>> requestWrapper = new AtomicReference<>(request);
        Flux<HttpResponse<?>> streamResponsePublisher = connectAndStream(parentRequest, request, requestURI, requestWrapper, false, true);

        streamResponsePublisher = readBodyOnError(errorType, streamResponsePublisher);

        // apply filters
        streamResponsePublisher = Flux.from(
                applyFilterToResponsePublisher(parentRequest, request, requestURI, streamResponsePublisher)
        );

        return streamResponsePublisher;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(@NonNull io.micronaut.http.HttpRequest<?> request) {
        return proxy(request, ProxyRequestOptions.getDefault());
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(@NonNull io.micronaut.http.HttpRequest<?> request, @NonNull ProxyRequestOptions options) {
        Objects.requireNonNull(options, "options");
        setupConversionService(request);
        return Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> {
                    MutableHttpRequest<?> httpRequest = toMutableRequest(request);
                    if (!options.isRetainHostHeader()) {
                        httpRequest.headers(headers -> headers.remove(HttpHeaderNames.HOST));
                    }

                    AtomicReference<MutableHttpRequest<?>> requestWrapper = new AtomicReference<>(httpRequest);
                    Flux<HttpResponse<?>> proxyResponsePublisher = connectAndStream(request, request, requestURI, requestWrapper, true, false);
                    // apply filters
                    //noinspection
                    proxyResponsePublisher = Flux.from(
                            applyFilterToResponsePublisher(
                                    request,
                                    requestWrapper.get(),
                                    requestURI,
                                    proxyResponsePublisher
                            )
                    );
                    return proxyResponsePublisher.map(HttpResponse::toMutableResponse);
                });
    }

    private void setupConversionService(io.micronaut.http.HttpRequest<?> httpRequest) {
        if (httpRequest instanceof ConversionServiceAware aware) {
            aware.setConversionService(conversionService);
        }
    }

    private <I> Flux<HttpResponse<?>> connectAndStream(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            AtomicReference<MutableHttpRequest<?>> requestWrapper,
            boolean isProxy,
            boolean failOnError
    ) {
        RequestKey requestKey;
        try {
            requestKey = new RequestKey(this, requestURI);
        } catch (Exception e) {
            return Flux.error(e);
        }
        return connectionManager.connect(requestKey, null).flatMapMany(poolHandle -> {
            request.setAttribute(NettyClientHttpRequest.CHANNEL, poolHandle.channel);

            boolean sse = !isProxy && isAcceptEvents(request);
            if (sse) {
                poolHandle.channel.pipeline().addLast(HttpLineBasedFrameDecoder.NAME, new HttpLineBasedFrameDecoder(configuration.getMaxContentLength(), true, true));
            }

            return this.streamRequestThroughChannel(
                parentRequest,
                requestWrapper.get(),
                poolHandle,
                failOnError,
                requestKey.isSecure()
            );
        });
    }

    /**
     * Implementation of {@link #exchange(io.micronaut.http.HttpRequest, Argument, Argument)} (after URI resolution).
     */
    private <I, E> Publisher<HttpResponse<?>> exchangeImpl(
        URI requestURI,
        io.micronaut.http.HttpRequest<?> parentRequest,
        MutableHttpRequest<I> request,
        @NonNull Argument<?> bodyType,
        @NonNull Argument<E> errorType,
        @Nullable BlockHint blockHint) {
        AtomicReference<MutableHttpRequest<?>> requestWrapper = new AtomicReference<>(request);

        RequestKey requestKey;
        try {
            requestKey = new RequestKey(this, requestURI);
        } catch (HttpClientException e) {
            return Flux.error(e);
        }

        Mono<ConnectionManager.PoolHandle> handlePublisher = connectionManager.connect(requestKey, blockHint);

        Flux<HttpResponse<?>> responsePublisher = handlePublisher.flatMapMany(poolHandle -> {
            poolHandle.channel.pipeline()
                .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_AGGREGATOR, new HttpObjectAggregator(configuration.getMaxContentLength()) {
                    @Override
                    protected void finishAggregation(FullHttpMessage aggregated) throws Exception {
                        // only set content-length if there's any content
                        if (!HttpUtil.isContentLengthSet(aggregated) &&
                            aggregated.content().readableBytes() > 0) {
                            super.finishAggregation(aggregated);
                        }
                    }
                });

            return Flux.create(emitter -> {
                try {
                    sendRequestThroughChannel(
                        requestWrapper.get(),
                        bodyType,
                        errorType,
                        emitter,
                        poolHandle
                    );
                } catch (Exception e) {
                    emitter.error(e);
                }
            });
        });

        Publisher<HttpResponse<?>> finalPublisher = applyFilterToResponsePublisher(
            parentRequest,
            request,
            requestURI,
            responsePublisher
        );
        Flux<HttpResponse<?>> finalReactiveSequence = Flux.from(finalPublisher);
        Duration requestTimeout = configuration.getRequestTimeout();
        if (requestTimeout == null) {
            // for compatibility
            requestTimeout = configuration.getReadTimeout()
                .filter(d -> !d.isNegative())
                .map(d -> d.plusSeconds(1)).orElse(null);
        }
        if (requestTimeout != null) {
            if (!requestTimeout.isNegative()) {
                finalReactiveSequence = finalReactiveSequence.timeout(requestTimeout)
                    .onErrorResume(throwable -> {
                        if (throwable instanceof TimeoutException) {
                            return Flux.error(ReadTimeoutException.TIMEOUT_EXCEPTION);
                        }
                        return Flux.error(throwable);
                    });
            }
        }
        return finalReactiveSequence;
    }

    /**
     * @param request The request
     * @param <I>     The input type
     * @return A {@link Publisher} with the resolved URI
     */
    protected <I> Publisher<URI> resolveRequestURI(io.micronaut.http.HttpRequest<I> request) {
        return resolveRequestURI(request, true);
    }

    /**
     * @param request            The request
     * @param includeContextPath Whether to prepend the client context path
     * @param <I>                The input type
     * @return A {@link Publisher} with the resolved URI
     */
    protected <I> Publisher<URI> resolveRequestURI(io.micronaut.http.HttpRequest<I> request, boolean includeContextPath) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return Flux.just(requestURI);
        } else {
            return resolveURI(request, includeContextPath);
        }
    }

    /**
     * @param parentRequest      The parent request
     * @param request            The redirect location request
     * @param <I>                The input type
     * @return A {@link Publisher} with the resolved URI
     */
    protected <I> Publisher<URI> resolveRedirectURI(io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<I> request) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return Flux.just(requestURI);
        } else {
            if (parentRequest == null || parentRequest.getUri().getHost() == null) {
                return resolveURI(request, false);
            } else {
                URI parentURI = parentRequest.getUri();
                UriBuilder uriBuilder = UriBuilder.of(requestURI)
                        .scheme(parentURI.getScheme())
                        .userInfo(parentURI.getUserInfo())
                        .host(parentURI.getHost())
                        .port(parentURI.getPort());
                return Flux.just(uriBuilder.build());
            }
        }
    }

    /**
     * @return The discriminator to use when selecting a server for the purposes of load balancing (defaults to null)
     */
    protected Object getLoadBalancerDiscriminator() {
        return null;
    }

    private <I> Publisher<HttpResponse<?>> applyFilterToResponsePublisher(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            Publisher<HttpResponse<?>> responsePublisher) {

        if (!(request instanceof MutableHttpRequest<?> mutRequest)) {
            return responsePublisher;
        }

        mutRequest.uri(requestURI);
        if (informationalServiceId != null && mutRequest.getAttribute(HttpAttributes.SERVICE_ID).isEmpty()) {

            mutRequest.setAttribute(HttpAttributes.SERVICE_ID, informationalServiceId);
        }

        List<GenericHttpFilter> filters =
                filterResolver.resolveFilters(request, clientFilterEntries);
        if (parentRequest != null) {
            // todo: migrate to new filter
            filters.add(
                GenericHttpFilter.createLegacyFilter(new ClientServerContextFilter(parentRequest), new FilterOrder.Fixed(Ordered.HIGHEST_PRECEDENCE))
            );
        }

        FilterRunner.sortReverse(filters);

        FilterRunner runner = new FilterRunner(filters) {
            @Override
            protected ExecutionFlow<HttpResponse<?>> provideResponse(io.micronaut.http.HttpRequest<?> request, PropagatedContext propagatedContext) {
                try {
                    try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                        return ReactiveExecutionFlow.fromPublisher(responsePublisher);
                    }
                } catch (Throwable e) {
                    return ExecutionFlow.error(e);
                }
            }
        };
        Mono<HttpResponse<?>> responseMono = Mono.from(ReactiveExecutionFlow.fromFlow(runner.run(request)).toPublisher());
        if (parentRequest != null) {
            responseMono = responseMono.contextWrite(c -> {
                // existing entry takes precedence. The parentRequest is derived from a thread
                // local, and is more likely to be wrong than any reactive context we are fed.
                if (c.hasKey(ServerRequestContext.KEY)) {
                    return c;
                } else {
                    return c.put(ServerRequestContext.KEY, parentRequest);
                }
            });
        }
        return responseMono;
    }

    /**
     * @param request                The request
     * @param requestURI             The URI of the request
     * @param requestContentType     The request content type
     * @param permitsBody            Whether permits body
     * @param onError                Called when the body publisher encounters an error
     * @return The body
     * @throws HttpPostRequestEncoder.ErrorDataEncoderException if there is an encoder exception
     */
    private NettyByteBody buildNettyRequest(
        MutableHttpRequest<?> request,
        URI requestURI,
        MediaType requestContentType,
        boolean permitsBody,
        Consumer<? super Throwable> onError,
        EventLoop eventLoop) throws HttpPostRequestEncoder.ErrorDataEncoderException {

        if (!request.getHeaders().contains(io.micronaut.http.HttpHeaders.HOST)) {
            request.getHeaders().set(HttpHeaderNames.HOST, getHostHeader(requestURI));
        }

        if (permitsBody) {
            Optional<?> body = request.getBody();
            if (body.isPresent()) {
                if (!request.getHeaders().contains(io.micronaut.http.HttpHeaders.CONTENT_TYPE)) {
                    MediaType mediaType = request.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
                    request.getHeaders().set(HttpHeaderNames.CONTENT_TYPE, mediaType);
                }
            }
        }

        NettyHttpRequestBuilder nettyRequestBuilder = NettyHttpRequestBuilder.asBuilder(request);
        Optional<ByteBody> direct = nettyRequestBuilder.byteBodyDirect();
        if (direct.isPresent()) {
            return NettyBodyAdapter.adapt(direct.get(), eventLoop);
        }

        if (permitsBody) {
            Optional<?> body = request.getBody();
            boolean hasBody = body.isPresent();
            if (requestContentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE) && hasBody) {
                Object bodyValue = body.get();
                if (bodyValue instanceof CharSequence sequence) {
                    ByteBuf byteBuf = charSequenceToByteBuf(sequence, requestContentType);
                    return new AvailableNettyByteBody(byteBuf);
                } else {
                    return buildFormRequest(request, eventLoop, r -> buildFormDataRequest(r, bodyValue));
                }
            } else if (requestContentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE) && hasBody) {
                return buildFormRequest(request, eventLoop, r -> buildMultipartRequest(r, body.get()));
            } else {
                ByteBuf bodyContent;
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

                        requestBodyPublisher = requestBodyPublisher.doOnError(onError);

                        return NettyBodyAdapter.adapt(requestBodyPublisher.map(ByteBufHolder::content), eventLoop, nettyRequestBuilder.toHttpRequestWithoutBody().headers(), null);
                    } else if (bodyValue instanceof CharSequence sequence) {
                        bodyContent = charSequenceToByteBuf(sequence, requestContentType);
                    } else {
                        Argument<Object> type = Argument.ofInstance(bodyValue);
                        ByteBuffer<?> buffer = handlerRegistry.getWriter(type, List.of(requestContentType))
                            .writeTo(type, requestContentType, bodyValue, request.getHeaders(), byteBufferFactory);
                        bodyContent = (ByteBuf) buffer.asNativeBuffer();
                    }
                    if (bodyContent == null) {
                        bodyContent = conversionService.convert(bodyValue, ByteBuf.class).orElseThrow(() ->
                            decorate(new HttpClientException("Body [" + bodyValue + "] cannot be encoded to content type [" + requestContentType + "]. No possible codecs or converters found."))
                        );
                    }
                } else {
                    bodyContent = Unpooled.EMPTY_BUFFER;
                }
                return new AvailableNettyByteBody(bodyContent);
            }
        } else {
            return (NettyByteBody) AvailableNettyByteBody.empty();
        }
    }

    private static boolean requiresRequestBody(HttpMethod method) {
        return method != null && (method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT) || method.equals(HttpMethod.PATCH));
    }

    private static boolean permitsRequestBody(HttpMethod method) {
        return method != null && (requiresRequestBody(method)
            || method.equals(HttpMethod.OPTIONS)
            || method.equals(HttpMethod.DELETE)
        );
    }

    private Flux<HttpResponse<?>> readBodyOnError(@Nullable Argument<?> errorType, @NonNull Flux<HttpResponse<?>> publisher) {
        if (errorType != null && errorType != HttpClient.DEFAULT_ERROR_TYPE) {
            return publisher.onErrorResume(clientException -> {
                if (clientException instanceof HttpClientResponseException exception) {
                    final HttpResponse<?> response = exception.getResponse();
                    if (response instanceof NettyStreamedHttpResponse<?> streamedResponse) {
                        return Mono.create(emitter -> {
                            final StreamedHttpResponse nettyResponse = streamedResponse.getNettyResponse();
                            nettyResponse.subscribe(new Subscriber<>() {
                                final CompositeByteBuf buffer = byteBufferFactory.getNativeAllocator().compositeBuffer();
                                Subscription s;
                                @Override
                                public void onSubscribe(Subscription s) {
                                    this.s = s;
                                    s.request(1);
                                }

                                @Override
                                public void onNext(HttpContent httpContent) {
                                    buffer.addComponent(true, httpContent.content());
                                    s.request(1);
                                }

                                @Override
                                public void onError(Throwable t) {
                                    buffer.release();
                                    emitter.error(t);
                                }

                                @Override
                                public void onComplete() {
                                    try {
                                        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(nettyResponse.protocolVersion(), nettyResponse.status(), buffer, nettyResponse.headers(), new DefaultHttpHeaders(true));
                                        final FullNettyClientHttpResponse<Object> fullNettyClientHttpResponse = new FullNettyClientHttpResponse<>(fullHttpResponse, handlerRegistry, (Argument<Object>) errorType, true, conversionService);
                                        emitter.error(decorate(new HttpClientResponseException(
                                            fullHttpResponse.status().reasonPhrase(),
                                            null,
                                            fullNettyClientHttpResponse,
                                            new HttpClientErrorDecoder() {
                                                @Override
                                                public Argument<?> getErrorType(MediaType mediaType) {
                                                    return errorType;
                                                }
                                            }
                                        )));
                                    } finally {
                                        buffer.release();
                                    }
                                }
                            });
                        });
                    }
                }
                return Mono.error(clientException);
            });
        }
        return publisher;
    }

    private <I> Publisher<URI> resolveURI(io.micronaut.http.HttpRequest<I> request, boolean includeContextPath) {
        URI requestURI = request.getUri();
        if (loadBalancer == null) {
            return Flux.error(decorate(new NoHostException("Request URI specifies no host to connect to")));
        }

        return Flux.from(loadBalancer.select(getLoadBalancerDiscriminator())).map(server -> {
                    Optional<String> authInfo = server.getMetadata().get(io.micronaut.http.HttpHeaders.AUTHORIZATION_INFO, String.class);
                    if (request instanceof MutableHttpRequest<?> httpRequest && authInfo.isPresent()) {
                        httpRequest.getHeaders().auth(authInfo.get());
                    }

                    try {
                        return server.resolve(includeContextPath ? ContextPathUtils.prepend(requestURI, contextPath) : requestURI);
                    } catch (URISyntaxException e) {
                        throw decorate(new HttpClientException("Failed to construct the request URI", e));
                    }
                }
        );
    }

    private <I, O, E> void sendRequestThroughChannel(
            io.micronaut.http.HttpRequest<I> finalRequest,
            Argument<O> bodyType,
            Argument<E> errorType,
            FluxSink<? super HttpResponse<O>> emitter,
            ConnectionManager.PoolHandle poolHandle) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        URI requestURI = finalRequest.getUri();
        MediaType requestContentType = finalRequest
                .getContentType()
                .orElse(MediaType.APPLICATION_JSON_TYPE);

        boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(finalRequest.getMethod());

        NettyByteBody bytes = buildNettyRequest(
            (MutableHttpRequest) finalRequest,
            requestURI,
            requestContentType,
            permitsBody,
            throwable -> {
                if (!emitter.isCancelled()) {
                    emitter.error(throwable);
                }
            },
            poolHandle.channel.eventLoop()
        );
        String newUri = requestURI.getRawPath();
        if (requestURI.getRawQuery() != null) {
            newUri += "?" + requestURI.getRawQuery();
        }
        HttpRequest nettyRequest = NettyHttpRequestBuilder.asBuilder(finalRequest).toHttpRequestWithoutBody().setUri(newUri);

        if (log.isDebugEnabled()) {
            debugRequest(requestURI, nettyRequest);
        }

        if (log.isTraceEnabled()) {
            traceRequest(finalRequest, nettyRequest);
        }

        Promise<HttpResponse<O>> responsePromise = poolHandle.channel.eventLoop().newPromise();
        poolHandle.channel.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE,
            new FullHttpResponseHandler<>(responsePromise, poolHandle, finalRequest, bodyType, errorType));
        poolHandle.notifyRequestPipelineBuilt();
        Publisher<HttpResponse<O>> publisher = new NettyFuturePublisher<>(responsePromise, true);
        publisher.subscribe(new ForwardingSubscriber<>(emitter));

        new ByteBodyRequestWriter(nettyRequest, bytes).write(poolHandle);
    }

    private Flux<MutableHttpResponse<?>> streamRequestThroughChannel(
            io.micronaut.http.HttpRequest<?> parentRequest,
            MutableHttpRequest<?> request,
            ConnectionManager.PoolHandle poolHandle,
            boolean failOnError,
            boolean secure) {
        return Flux.<MutableHttpResponse<?>>create(sink -> {
            try {
                streamRequestThroughChannel0(parentRequest, request, sink, poolHandle);
            } catch (HttpPostRequestEncoder.ErrorDataEncoderException e) {
                sink.error(e);
            }
        }).flatMap(resp -> handleStreamHttpError(resp, failOnError));
    }

    private <R extends HttpResponse<?>> Flux<R> handleStreamHttpError(
            R response,
            boolean failOnError
    ) {
        boolean errorStatus = response.code() >= 400;
        if (errorStatus && failOnError) {
            return Flux.error(decorate(new HttpClientResponseException(response.reason(), response)));
        } else {
            return Flux.just(response);
        }
    }

    private void streamRequestThroughChannel0(
        io.micronaut.http.HttpRequest<?> parentRequest,
        MutableHttpRequest<?> request,
        FluxSink<? super MutableHttpResponse<?>> emitter,
        ConnectionManager.PoolHandle poolHandle) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        URI requestURI = request.getUri();
        boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod());
        NettyByteBody byteBody = buildNettyRequest(
            request,
            requestURI,
            request
                .getContentType()
                .orElse(MediaType.APPLICATION_JSON_TYPE),
            permitsBody,
            throwable -> {
                if (!emitter.isCancelled()) {
                    emitter.error(throwable);
                }
            },
            poolHandle.channel.eventLoop()
        );
        String newUri = requestURI.getRawPath();
        if (requestURI.getRawQuery() != null) {
            newUri += "?" + requestURI.getRawQuery();
        }
        HttpRequest nettyRequest = NettyHttpRequestBuilder.asBuilder(request).toHttpRequestWithoutBody().setUri(newUri);

        Promise<MutableHttpResponse<?>> responsePromise = poolHandle.channel.eventLoop().newPromise();
        ChannelPipeline pipeline = poolHandle.channel.pipeline();
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, new StreamHttpResponseHandler(responsePromise, parentRequest, request, poolHandle));
        poolHandle.notifyRequestPipelineBuilt();

        if (log.isDebugEnabled()) {
            debugRequest(request.getUri(), nettyRequest);
        }

        if (log.isTraceEnabled()) {
            traceRequest(request, nettyRequest);
        }

        new ByteBodyRequestWriter(nettyRequest, byteBody).write(poolHandle);
        responsePromise.addListener((Future<MutableHttpResponse<?>> future) -> {
            if (future.isSuccess()) {
                emitter.next(future.getNow());
                emitter.complete();
            } else {
                emitter.error(future.cause());
            }
        });
    }

    private ByteBuf charSequenceToByteBuf(CharSequence bodyValue, MediaType requestContentType) {
        return byteBufferFactory.copiedBuffer(
                bodyValue.toString().getBytes(
                        requestContentType.getCharset().orElse(defaultCharset)
                )
        ).asNativeBuffer();
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

    private NettyByteBody buildFormRequest(
        MutableHttpRequest<?> request,
        EventLoop eventLoop,
        ThrowingFunction<HttpRequest, HttpPostRequestEncoder, HttpPostRequestEncoder.ErrorDataEncoderException> buildMethod
    ) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        // this function acts like a wrapper around HttpPostRequestEncoder. HttpPostRequestEncoder
        // takes a request + form data and transforms it to a request + bytes. Because we only want
        // the bytes, we need to copy the data from the netty request back to the original
        // MutableHttpRequest. This is just the Content-Type header, which sometimes gets an extra
        // boundary specifier that we need.

        // build the mock netty request (only the content-type matters)
        HttpRequest nettyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        List<AsciiString> relevantHeaders = List.of(HttpHeaderNames.CONTENT_TYPE);
        for (AsciiString header : relevantHeaders) {
            nettyRequest.headers().add(header, request.getHeaders().getAll(header));
        }

        HttpPostRequestEncoder encoder = buildMethod.apply(nettyRequest);
        HttpRequest finalized = encoder.finalizeRequest();
        // copy back the content-type
        for (AsciiString header : relevantHeaders) {
            request.getHeaders().remove(header);
            for (String value : finalized.headers().getAll(header)) {
                request.getHeaders().add(header, value);
            }
        }
        // return the body bytes
        if (encoder.isChunked()) {
            Flux<ByteBuf> bytes = Flux.create(em -> {
                em.onRequest(n -> {
                    try {
                        while (n-- > 0) {
                            HttpContent chunk = encoder.readChunk(PooledByteBufAllocator.DEFAULT);
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
            return NettyBodyAdapter.adapt(bytes, eventLoop);
        } else {
            return new AvailableNettyByteBody(((FullHttpRequest) finalized).content());
        }
    }

    private HttpPostRequestEncoder buildFormDataRequest(HttpRequest baseRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(baseRequest, false);

        Map<String, Object> formData;
        if (bodyValue instanceof Map) {
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
                @NonNull
                @Override
                public InterfaceHttpData createFileUpload(@NonNull String name, @NonNull String filename, @NonNull MediaType contentType, @Nullable String encoding, @Nullable Charset charset, long length) {
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

                @NonNull
                @Override
                public InterfaceHttpData createAttribute(@NonNull String name, @NonNull String value) {
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

    private void debugRequest(URI requestURI, HttpRequest nettyRequest) {
        log.debug("Sending HTTP {} to {}",
                nettyRequest.method(),
                requestURI.toString());
    }

    private void traceRequest(io.micronaut.http.HttpRequest<?> request, HttpRequest nettyRequest) {
        HttpHeaders headers = nettyRequest.headers();
        HttpHeadersUtil.trace(log, headers.names(), headers::getAll);
        if (io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod()) && request.getBody().isPresent() && nettyRequest instanceof FullHttpRequest fullHttpRequest) {
            ByteBuf content = fullHttpRequest.content();
            if (log.isTraceEnabled()) {
                traceBody("Request", content);
            }
        }
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
            new NettyWritableBodyWriter(applicationConfiguration)
        );
        JsonMapper mapper = JsonMapper.createDefault();
        registry.add(MediaType.APPLICATION_JSON_TYPE, new NettyJsonHandler<>(mapper));
        registry.add(MediaType.APPLICATION_JSON_TYPE, new NettyCharSequenceBodyWriter());
        registry.add(MediaType.APPLICATION_JSON_STREAM_TYPE, new NettyJsonStreamHandler<>(mapper));
        return registry;
    }

    static boolean isSecureScheme(String scheme) {
        return io.micronaut.http.HttpRequest.SCHEME_HTTPS.equalsIgnoreCase(scheme) || SCHEME_WSS.equalsIgnoreCase(scheme);
    }

    private <E extends HttpClientException> E decorate(E exc) {
        return HttpClientExceptionUtils.populateServiceId(exc, informationalServiceId, configuration);
    }

    /**
     * Key used for connection pooling and determining host/port.
     */
    public static final class RequestKey {
        private final String host;
        private final int port;
        private final boolean secure;

        /**
         * @param ctx The HTTP client that created this request key. Only used for exception
         *            context, not stored
         * @param requestURI The request URI
         */
        public RequestKey(DefaultHttpClient ctx, URI requestURI) {
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
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RequestKey that = (RequestKey) o;
            return port == that.port &&
                    secure == that.secure &&
                    Objects.equals(host, that.host);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(host, port, secure);
        }

        private <E extends HttpClientException> E decorate(DefaultHttpClient ctx, E exc) {
            return HttpClientExceptionUtils.populateServiceId(exc, ctx.informationalServiceId, ctx.configuration);
        }
    }

    private abstract static class ContinueHandler extends ChannelInboundHandlerAdapter {
        private boolean continued;

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            if (!continued) {
                discard();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            io.netty.handler.codec.http.HttpResponse response = (io.netty.handler.codec.http.HttpResponse) msg;
            if (response.status() == HttpResponseStatus.CONTINUE) {
                continued = true;
                continueBody(ctx);
            }
            ctx.pipeline().remove(this);
        }

        protected abstract void discard();

        protected abstract void continueBody(ChannelHandlerContext ctx);
    }

    private record ByteBodyRequestWriter(HttpRequest nettyRequest, NettyByteBody byteBody) {
        ByteBodyRequestWriter {
            HttpHeaders headers = nettyRequest.headers();
            OptionalLong length = byteBody.expectedLength();
            if (length.isPresent()) {
                headers.remove(HttpHeaderNames.TRANSFER_ENCODING);
                if (length.getAsLong() != 0 || permitsRequestBody(nettyRequest.method())) {
                    headers.set(HttpHeaderNames.CONTENT_LENGTH, length.getAsLong());
                }
            } else {
                headers.remove(HttpHeaderNames.CONTENT_LENGTH);
                headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
        }

        public void write(ConnectionManager.PoolHandle poolHandle) {
            if (!poolHandle.http2) {
                if (poolHandle.canReturn()) {
                    nettyRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                } else {
                    nettyRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
                }
            }

            Channel channel = poolHandle.channel();
            if (byteBody instanceof AvailableNettyByteBody available) {
                ByteBuf byteBuf = AvailableNettyByteBody.toByteBuf(available);
                if (HttpUtil.is100ContinueExpected(nettyRequest)) {
                    channel.writeAndFlush(nettyRequest, channel.voidPromise());
                    channel.pipeline().addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, "continue-handler", new ContinueHandler() {
                        @Override
                        protected void discard() {
                            byteBuf.release();
                        }

                        @Override
                        protected void continueBody(ChannelHandlerContext ctx) {
                            ctx.writeAndFlush(new DefaultLastHttpContent(byteBuf), ctx.voidPromise());
                        }
                    });
                } else {
                    // it's a bit more efficient to use a full request for HTTP/2
                    channel.writeAndFlush(new DefaultFullHttpRequest(
                        nettyRequest.protocolVersion(),
                        nettyRequest.method(),
                        nettyRequest.uri(),
                        byteBuf,
                        nettyRequest.headers(),
                        EmptyHttpHeaders.INSTANCE
                    ), channel.voidPromise());
                }
            } else {
                StreamWriter streamWriter = new StreamWriter();
                streamWriter.upstream = ((StreamingNettyByteBody) byteBody).primary(streamWriter);

                channel.writeAndFlush(nettyRequest, channel.voidPromise());
                if (HttpUtil.is100ContinueExpected(nettyRequest)) {
                    channel.pipeline().addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, "continue-handler", new ContinueHandler() {
                        @Override
                        protected void discard() {
                            streamWriter.upstream.allowDiscard();
                            streamWriter.upstream.disregardBackpressure();
                        }

                        @Override
                        protected void continueBody(ChannelHandlerContext ctx) {
                            channel.pipeline().addLast(streamWriter);
                        }
                    });
                } else {
                    channel.pipeline().addLast(streamWriter);
                }
            }
        }
    }

    private static class StreamWriter extends ChannelInboundHandlerAdapter implements BufferConsumer {
        ChannelHandlerContext ctx;
        EventLoopFlow flow;
        Upstream upstream;
        long unwritten = 0;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            this.ctx = ctx;
            this.flow = new EventLoopFlow(ctx.channel().eventLoop());

            upstream.start();
        }

        @Override
        public void add(ByteBuf buf) {
            if (flow.executeNow(() -> add0(buf))) {
                add0(buf);
            }
        }

        private void add0(ByteBuf buf) {
            if (ctx == null) {
                // discarded
                buf.release();
                return;
            }

            int readable = buf.readableBytes();
            ctx.writeAndFlush(buf).addListener((ChannelFutureListener) future -> {
                assert ctx.executor().inEventLoop();
                if (future.isSuccess()) {
                    if (ctx.channel().isWritable()) {
                        upstream.onBytesConsumed(readable);
                    } else {
                        unwritten += readable;
                    }
                }
            });
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            long unwritten = this.unwritten;
            if (ctx.channel().isWritable() && unwritten != 0) {
                this.unwritten = 0;
                upstream.onBytesConsumed(unwritten);
            }
            super.channelWritabilityChanged(ctx);
        }

        @Override
        public void complete() {
            if (flow.executeNow(this::complete0)) {
                complete0();
            }
        }

        private void complete0() {
            if (ctx == null) {
                // discarded
                return;
            }

            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, ctx.voidPromise());
            ctx.pipeline().remove(ctx.name());
        }

        @Override
        public void discard() {
        }

        @Override
        public void error(Throwable e) {
            if (ctx == null) {
                // discarded
                return;
            }

            ctx.fireExceptionCaught(e);
            ctx.pipeline().remove(ctx.name());
        }
    }

    /**
     * Used as a holder for the current SSE event.
     */
    private static class CurrentEvent {
        byte[] data;
        String id;
        String name;
        Duration retry;
    }

    private abstract class BaseHttpResponseHandler<O> extends SimpleChannelInboundHandlerInstrumented<HttpObject> {
        final Promise<? super O> responsePromise;
        final io.micronaut.http.HttpRequest<?> parentRequest;
        final io.micronaut.http.HttpRequest<?> finalRequest;

        public BaseHttpResponseHandler(Promise<? super O> responsePromise, io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<?> finalRequest) {
            super(false);
            this.responsePromise = responsePromise;
            this.parentRequest = parentRequest;
            this.finalRequest = finalRequest;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            ctx.read();
        }

        @Override
        public final boolean acceptInboundMessage(Object msg) {
            return msg instanceof HttpObject;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            String message = cause.getMessage();
            if (message == null) {
                message = cause.getClass().getSimpleName();
            }
            if (log.isTraceEnabled()) {
                log.trace("HTTP Client exception ({}) occurred for request : {} {}",
                    message, finalRequest.getMethodName(), finalRequest.getUri());
            }

            HttpClientException result;
            if (cause instanceof TooLongFrameException) {
                result = decorate(new ContentLengthExceededException(configuration.getMaxContentLength()));
            } else if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
                result = ReadTimeoutException.TIMEOUT_EXCEPTION;
            } else {
                result = decorate(new HttpClientException("Error occurred reading HTTP response: " + message, cause));
            }
            responsePromise.tryFailure(result);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // connection became inactive before this handler was removed (i.e. before channelRead)
            responsePromise.tryFailure(new ResponseClosedException("Connection closed before response was received"));
            ctx.fireChannelInactive();
        }

        private static void setRedirectHeaders(@Nullable io.micronaut.http.HttpRequest<?> request, MutableHttpRequest<Object> redirectRequest) {
            if (request != null) {
                for (Map.Entry<String, List<String>> originalHeader : request.getHeaders()) {
                    if (!REDIRECT_HEADER_BLOCKLIST.contains(originalHeader.getKey())) {
                        final List<String> originalHeaderValue = originalHeader.getValue();
                        if (originalHeaderValue != null && !originalHeaderValue.isEmpty()) {
                            for (String value : originalHeaderValue) {
                                if (value != null) {
                                    redirectRequest.header(originalHeader.getKey(), value);
                                }
                            }
                        }
                    }
                }
            }
        }

        protected final boolean handleResponse(io.netty.handler.codec.http.HttpResponse msg) {
            int code = msg.status().code();
            HttpHeaders headers1 = msg.headers();
            if (code > 300 && code < 400 && configuration.isFollowRedirects() && headers1.contains(HttpHeaderNames.LOCATION)) {
                String location = headers1.get(HttpHeaderNames.LOCATION);

                MutableHttpRequest<Object> redirectRequest;
                if (code == 307 || code == 308) {
                    redirectRequest = io.micronaut.http.HttpRequest.create(finalRequest.getMethod(), location);
                    finalRequest.getBody().ifPresent(redirectRequest::body);
                } else {
                    redirectRequest = io.micronaut.http.HttpRequest.GET(location);
                }

                setRedirectHeaders(finalRequest, redirectRequest);
                Flux.from(resolveRedirectURI(parentRequest, redirectRequest))
                    .flatMap(makeRedirectHandler(parentRequest, redirectRequest))
                    .subscribe(new NettyPromiseSubscriber<>(responsePromise));
                return false;
            } else {
                HttpHeaders headers = msg.headers();
                if (log.isTraceEnabled()) {
                    log.trace("HTTP Client Response Received ({}) for Request: {} {}", msg.status(), finalRequest.getMethodName(), finalRequest.getUri());
                    HttpHeadersUtil.trace(log, headers.names(), headers::getAll);
                }
                return true;
            }
        }

        protected abstract Function<URI, Publisher<? extends O>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest);
    }

    private final class FullHttpResponseHandler<O> extends BaseHttpResponseHandler<HttpResponse<O>> {
        private final Argument<O> bodyType;
        private final Argument<?> errorType;
        private final ConnectionManager.PoolHandle poolHandle;

        public FullHttpResponseHandler(
            Promise<HttpResponse<O>> responsePromise,
            ConnectionManager.PoolHandle poolHandle,
            io.micronaut.http.HttpRequest<?> request,
            Argument<O> bodyType,
            Argument<?> errorType) {
            super(responsePromise, request, request);
            this.bodyType = bodyType;
            this.errorType = errorType;
            this.poolHandle = poolHandle;
        }

        @Override
        protected Function<URI, Publisher<? extends HttpResponse<O>>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest) {
            return uri -> (Publisher) exchangeImpl(uri, parentRequest, redirectRequest, bodyType, errorType, null);
        }

        @Override
        protected void channelReadInstrumented(ChannelHandlerContext ctx, HttpObject obj) throws Exception {
            if (!(obj instanceof FullHttpResponse fullResponse)) {
                ReferenceCountUtil.release(obj);
                exceptionCaught(ctx, new IllegalArgumentException("Expected full response"));
                return;
            }

            try {
                if (handleResponse(fullResponse)) {
                    forwardResponseToPromise(fullResponse);
                }
            } finally {
                if (!HttpUtil.isKeepAlive(fullResponse)) {
                    poolHandle.taint();
                }
                ctx.pipeline().remove(this);
                fullResponse.release();
            }
        }

        private void forwardResponseToPromise(FullHttpResponse msg) {
            try {
                if (log.isTraceEnabled()) {
                    traceBody("Response", msg.content());
                }

                if (msg.status().equals(HttpResponseStatus.NO_CONTENT)) {
                    // normalize the NO_CONTENT header, since http content aggregator adds it even if not present in the response
                    msg.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                }

                boolean convertBodyWithBodyType = shouldConvertWithBodyType(msg, DefaultHttpClient.this.configuration, bodyType, errorType);
                FullNettyClientHttpResponse<O> response
                        = new FullNettyClientHttpResponse<>(msg, handlerRegistry, bodyType, convertBodyWithBodyType, conversionService);

                if (convertBodyWithBodyType) {
                    responsePromise.trySuccess(response);
                } else { // error flow
                    try {
                        responsePromise.tryFailure(makeErrorFromRequestBody(msg.status(), response));
                    } catch (HttpClientResponseException t) {
                        responsePromise.tryFailure(t);
                    } catch (Exception t) {
                        responsePromise.tryFailure(makeErrorBodyParseError(msg, t));
                    }
                }
            } catch (HttpClientResponseException t) {
                responsePromise.tryFailure(t);
            } catch (Exception t) {
                makeNormalBodyParseError(msg, t, cause -> {
                    if (!responsePromise.tryFailure(cause) && log.isWarnEnabled()) {
                        log.warn("Exception fired after handler completed: {}", t.getMessage(), t);
                    }
                });
            }
        }

        private static <O, E> boolean shouldConvertWithBodyType(FullHttpResponse msg,
                                                                HttpClientConfiguration configuration,
                                                                Argument<O> bodyType,
                                                                Argument<E> errorType) {
            if (msg.status().code() < 400) {
                return true;
            }
            return !configuration.isExceptionOnErrorStatus() && bodyType.equalsType(errorType);

        }

        /**
         * Create a {@link HttpClientResponseException} from a response with a failed HTTP status.
         */
        private HttpClientResponseException makeErrorFromRequestBody(HttpResponseStatus status, FullNettyClientHttpResponse<?> response) {
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

        private void makeNormalBodyParseError(FullHttpResponse fullResponse, Throwable t, Consumer<HttpClientResponseException> forward) {
            FullNettyClientHttpResponse<Object> response = new FullNettyClientHttpResponse<>(
                    fullResponse,
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
            forward.accept(clientResponseError);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_HTTP_AGGREGATOR);
            poolHandle.release();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            super.exceptionCaught(ctx, cause);
            poolHandle.taint();
            ctx.pipeline().remove(this);
        }
    }

    private final class StreamHttpResponseHandler extends BaseHttpResponseHandler<MutableHttpResponse<?>> {
        static final String NAME_FLOW_CONTROL = ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE + "-flow-control";
        static final String NAME_PUBLISHER = ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE + "-publisher";

        private final ConnectionManager.PoolHandle poolHandle;
        private boolean handoff = false;

        public StreamHttpResponseHandler(
            Promise<? super MutableHttpResponse<?>> responsePromise,
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<?> finalRequest, ConnectionManager.PoolHandle poolHandle) {

            super(responsePromise, parentRequest, finalRequest);
            this.poolHandle = poolHandle;
        }

        private static boolean hasBody(io.netty.handler.codec.http.HttpResponse response) {
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
        protected void channelReadInstrumented(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            io.netty.handler.codec.http.HttpResponse response = (io.netty.handler.codec.http.HttpResponse) msg;
            if (handleResponse(response)) {
                Publisher<HttpContent> body;
                if (msg instanceof FullHttpResponse fhr) {
                    if (fhr.content().isReadable()) {
                        body = Publishers.just(new DefaultLastHttpContent(fhr.content()));
                    } else {
                        body = Publishers.empty();
                        fhr.release();
                    }
                } else if (!hasBody(response)) {
                    skipContent(ctx, msg);
                    body = Publishers.empty();
                } else {
                    boolean autoRead = ctx.channel().config().isAutoRead();
                    ctx.channel().config().setAutoRead(false);
                    FlowControlHandler flowControlHandler = new FlowControlHandler();
                    ReactiveClientReader reader = new ReactiveClientReader() {
                        @Override
                        protected void remove(ChannelHandlerContext ctx) {
                            ctx.pipeline().remove(NAME_FLOW_CONTROL);
                            ctx.pipeline().remove(NAME_PUBLISHER);
                            ctx.channel().config().setAutoRead(autoRead);
                            poolHandle.release();
                        }
                    };
                    ctx.pipeline()
                        .addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, NAME_FLOW_CONTROL, flowControlHandler)
                        .addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, NAME_PUBLISHER, reader);
                    handoff = true;
                    ctx.pipeline().remove(ctx.name());

                    body = reader;
                }

                DefaultStreamedHttpResponse nettyResponse = new DefaultStreamedHttpResponse(
                    response.protocolVersion(),
                    response.status(),
                    response.headers(),
                    body
                );
                responsePromise.trySuccess(new NettyStreamedHttpResponse<>(nettyResponse, conversionService));
            } else {
                skipContent(ctx, msg);
            }
        }

        private void skipContent(ChannelHandlerContext ctx, HttpObject msg) {
            if (!(msg instanceof LastHttpContent)) {
                // add a handler to skip any contents
                ctx.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ((HttpContent) msg).release();
                        if (msg instanceof LastHttpContent) {
                            ctx.pipeline().remove(ctx.name());
                        }
                    }

                    @Override
                    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                        poolHandle.release();
                    }
                });
                handoff = true;
            }
            ctx.pipeline().remove(ctx.name());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            super.exceptionCaught(ctx, cause);
            poolHandle.taint();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            if (!handoff) {
                poolHandle.release();
            }
        }

        @Override
        protected Function<URI, Publisher<? extends MutableHttpResponse<?>>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest) {
            return uri -> Mono.from(buildStreamExchange(parentRequest, redirectRequest, uri, null)).map(HttpResponse::toMutableResponse);
        }
    }
}
