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
import io.micronaut.core.async.propagation.ReactivePropagation;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.ConversionServiceAware;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.functional.ThrowingFunction;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpRequestWrapper;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
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
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientErrorDecoder;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientExceptionUtils;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.multipart.MultipartDataFactory;
import io.micronaut.http.client.netty.ssl.ClientSslBuilder;
import io.micronaut.http.client.netty.ssl.NettyClientSslBuilder;
import io.micronaut.http.client.netty.websocket.NettyWebSocketClientHandler;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ContextPathUtils;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.body.AvailableNettyByteBody;
import io.micronaut.http.netty.body.NettyBodyAdapter;
import io.micronaut.http.netty.body.NettyByteBody;
import io.micronaut.http.netty.body.NettyByteBufMessageBodyHandler;
import io.micronaut.http.netty.body.NettyJsonHandler;
import io.micronaut.http.netty.body.NettyJsonStreamHandler;
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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
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
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.FastThreadLocalThread;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
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
        RawHttpClient,
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
                return DefaultHttpClient.this.exchange(request, bodyType, errorType, blockHint).block();
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
                return DefaultHttpClient.this.isRunning();
            }

            @Override
            public BlockingHttpClient start() {
                return DefaultHttpClient.this.start().toBlocking();
            }

            @Override
            public BlockingHttpClient stop() {
                return DefaultHttpClient.this.stop().toBlocking();
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

    private static <T> Mono<T> toMono(ExecutionFlow<T> flow, PropagatedContext context) {
        return Mono.from(ReactivePropagation.propagate(context, ReactiveExecutionFlow.toPublisher(flow)));
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
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return new MicronautFlux<>(toMono(resolveRequestURI(request), propagatedContext)
            .flatMapMany(requestURI -> dataStreamImpl(toMutableRequest(request), errorType, propagatedContext, requestURI))
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
    public <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return exchangeStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return new MicronautFlux<>(toMono(resolveRequestURI(request), propagatedContext)
                .flatMapMany(uri -> exchangeStreamImpl(propagatedContext, toMutableRequest(request), errorType, uri)))
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
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return Flux.from(toMono(resolveRequestURI(request), propagatedContext)
                .flatMapMany(requestURI -> jsonStreamImpl(propagatedContext, toMutableRequest(request), type, errorType, requestURI)));
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
        return exchange(request, bodyType, errorType, null)
            // some tests expect flux...
            .flux();
    }

    @NonNull
    private <I, O, E> Mono<HttpResponse<O>> exchange(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType, @Nullable BlockHint blockHint) {
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        // if a connection is available immediately, we can use its executor for the timeout
        // instead of a random executor for the whole group
        AtomicReference<ScheduledExecutorService> scheduler = new AtomicReference<>(connectionManager.getGroup());
        ExecutionFlow<HttpResponse<O>> mono = resolveRequestURI(request).flatMap(uri -> {
            MutableHttpRequest<?> mutableRequest = toMutableRequest(request).uri(uri);
            //noinspection unchecked
            return sendRequestWithRedirects(
                propagatedContext,
                scheduler,
                blockHint,
                mutableRequest,
                (req, resp) -> InternalByteBody.bufferFlow(resp.byteBody())
                    .onErrorResume(t -> ExecutionFlow.error(handleResponseError(mutableRequest, t)))
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
                mono = mono.timeout(requestTimeout, scheduler.get(), null)
                    .onErrorResume(throwable -> {
                        if (throwable instanceof TimeoutException) {
                            return ExecutionFlow.error(ReadTimeoutException.TIMEOUT_EXCEPTION);
                        }
                        return ExecutionFlow.error(throwable);
                    });
            }
        }
        return toMono(mono, propagatedContext);
    }

    private <O, E> @NonNull ExecutionFlow<FullNettyClientHttpResponse<O>> handleExchangeResponse(Argument<O> bodyType, Argument<E> errorType, NettyClientByteBodyResponse resp, CloseableAvailableByteBody av) {
        ByteBuf buf = AvailableNettyByteBody.toByteBuf(av);
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
                .switchMap(resolvedURI -> connectWebSocket(resolvedURI, request, clientEndpointType, null));
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters) {
        WebSocketBean<T> webSocketBean = webSocketRegistry.getWebSocket(clientEndpointType);
        String uri = webSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class).orElse("/ws");
        uri = UriTemplate.of(uri).expand(parameters);
        MutableHttpRequest<Object> request = io.micronaut.http.HttpRequest.GET(uri);
        return toMono(resolveRequestURI(request), PropagatedContext.getOrEmpty()).flux()
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

        if (!isRunning()) {
            return Mono.error(decorate(new HttpClientException("The client is closed, unable to connect for websocket.")));
        }

        return connectionManager.connectForWebsocket(requestKey, handler)
            .then(handler.getHandshakeCompletedMono());
    }

    private <I> Flux<HttpResponse<ByteBuffer<?>>> exchangeStreamImpl(PropagatedContext propagatedContext, MutableHttpRequest<I> request, Argument<?> errorType, URI requestURI) {
        Flux<HttpResponse<?>> streamResponsePublisher = toMono(buildStreamExchange(propagatedContext, request, requestURI, errorType), propagatedContext).flux();
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

    private <I, O> Flux<O> jsonStreamImpl(PropagatedContext propagatedContext, MutableHttpRequest<I> request, Argument<O> type, Argument<?> errorType, URI requestURI) {
        return toMono(buildStreamExchange(propagatedContext, request, requestURI, errorType), propagatedContext).flux().switchMap(response -> {
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

    private <I> Flux<ByteBuffer<?>> dataStreamImpl(MutableHttpRequest<I> request, Argument<?> errorType, PropagatedContext propagatedContext, URI requestURI) {
        Flux<HttpResponse<?>> streamResponsePublisher = toMono(buildStreamExchange(propagatedContext, request, requestURI, errorType), propagatedContext).flux();
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
            @Nullable PropagatedContext propagatedContext,
            @NonNull MutableHttpRequest<I> request,
            @NonNull URI requestURI,
            @Nullable Argument<?> errorType) {
        return this.sendRequestWithRedirects(
            propagatedContext,
            null,
            request.uri(requestURI),
            (req, resp) -> {
                ByteBody bb = resp.byteBody();
                Publisher<HttpContent> body;
                if (!hasBody(resp)) {
                    resp.close();
                    body = Flux.empty();
                } else {
                    if (isAcceptEvents(req)) {
                        if (bb instanceof AvailableNettyByteBody anbb) {
                            // same semantics as the streaming branch, but this is eager so it's more
                            // lax wrt unclosed responses.
                            ByteBuf single = AvailableNettyByteBody.toByteBuf(anbb);
                            List<ByteBuf> parts = SseSplitter.split(single);
                            parts.get(parts.size() - 1).release();
                            body = Flux.fromIterable(parts.subList(0, parts.size() - 1)).map(DefaultHttpContent::new);
                        } else {
                            body = SseSplitter.split(NettyByteBody.toByteBufs(bb), sizeLimits()).map(DefaultHttpContent::new);
                        }
                    } else {
                        body = NettyByteBody.toByteBufs(bb).map(DefaultHttpContent::new);
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
    public Publisher<MutableHttpResponse<?>> proxy(@NonNull io.micronaut.http.HttpRequest<?> request) {
        return proxy(request, ProxyRequestOptions.getDefault());
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(@NonNull io.micronaut.http.HttpRequest<?> request, @NonNull ProxyRequestOptions options) {
        Objects.requireNonNull(options, "options");
        setupConversionService(request);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return toMono(resolveRequestURI(request)
                .flatMap(requestURI -> {
                    MutableHttpRequest<?> httpRequest = toMutableRequest(request);
                    if (!options.isRetainHostHeader()) {
                        httpRequest.headers(headers -> headers.remove(HttpHeaderNames.HOST));
                    }

                    return this.sendRequestWithRedirects(
                        propagatedContext,
                        null,
                        httpRequest.uri(requestURI),
                        (req, resp) -> {
                            Publisher<HttpContent> body;
                            if (!hasBody(resp)) {
                                resp.close();
                                body = Flux.empty();
                            } else {
                                body = NettyByteBody.toByteBufs(resp.byteBody()).map(DefaultHttpContent::new);
                            }

                            return ExecutionFlow.<HttpResponse<?>>just(toStreamingResponse(resp, body))
                                .flatMap(r -> handleStreamHttpError(r, false));
                        }
                    );
                })
            .map(HttpResponse::toMutableResponse), propagatedContext);
    }

    private void setupConversionService(io.micronaut.http.HttpRequest<?> httpRequest) {
        if (httpRequest instanceof ConversionServiceAware aware) {
            aware.setConversionService(conversionService);
        }
    }

    /**
     * @param request The request
     * @param <I>     The input type
     * @return A {@link Publisher} with the resolved URI
     */
    protected <I> ExecutionFlow<URI> resolveRequestURI(io.micronaut.http.HttpRequest<I> request) {
        return resolveRequestURI(request, true);
    }

    /**
     * @param request            The request
     * @param includeContextPath Whether to prepend the client context path
     * @param <I>                The input type
     * @return A {@link Publisher} with the resolved URI
     */
    protected <I> ExecutionFlow<URI> resolveRequestURI(io.micronaut.http.HttpRequest<I> request, boolean includeContextPath) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return ExecutionFlow.just(requestURI);
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
    protected <I> ExecutionFlow<URI> resolveRedirectURI(io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<I> request) {
        URI requestURI = request.getUri();
        if (requestURI.getScheme() != null) {
            // if the request URI includes a scheme then it is fully qualified so use the direct server
            return ExecutionFlow.just(requestURI);
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
                return ExecutionFlow.just(uriBuilder.build());
            }
        }
    }

    /**
     * @return The discriminator to use when selecting a server for the purposes of load balancing (defaults to null)
     */
    protected Object getLoadBalancerDiscriminator() {
        return null;
    }

    /**
     * @param request                The request
     * @param requestURI             The URI of the request
     * @param requestContentType     The request content type
     * @param permitsBody            Whether permits body
     * @return The body
     * @throws HttpPostRequestEncoder.ErrorDataEncoderException if there is an encoder exception
     */
    private NettyByteBody buildNettyRequest(
        MutableHttpRequest<?> request,
        URI requestURI,
        MediaType requestContentType,
        boolean permitsBody,
        EventLoop eventLoop) throws HttpPostRequestEncoder.ErrorDataEncoderException {

        if (!request.getHeaders().contains(HttpHeaderNames.HOST)) {
            request.getHeaders().set(HttpHeaderNames.HOST, getHostHeader(requestURI));
        }

        if (permitsBody) {
            Optional<?> body = request.getBody();
            if (body.isPresent()) {
                if (!request.getHeaders().contains(HttpHeaderNames.CONTENT_TYPE)) {
                    MediaType mediaType = request.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
                    request.getHeaders().set(HttpHeaderNames.CONTENT_TYPE, mediaType);
                }
            }
        }

        NettyHttpRequestBuilder nettyRequestBuilder = NettyHttpRequestBuilder.asBuilder(request);
        ByteBody direct = nettyRequestBuilder.byteBodyDirect();
        if (direct != null) {
            return NettyBodyAdapter.adapt(direct, eventLoop);
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

    private void completeExceptionallySafe(DelayedExecutionFlow<?> flow, Throwable exc) {
        if (!flow.tryCompleteExceptionally(exc)) {
            log.debug("Client exception suppressed because response flow already completed", exc);
        }
    }

    private ExecutionFlow<HttpResponse<?>> readBodyOnError(@Nullable Argument<?> errorType, @NonNull ExecutionFlow<HttpResponse<?>> publisher) {
        if (errorType != null && errorType != HttpClient.DEFAULT_ERROR_TYPE) {
            return publisher.onErrorResume(clientException -> {
                if (clientException instanceof HttpClientResponseException exception) {
                    final HttpResponse<?> response = exception.getResponse();
                    if (response instanceof NettyStreamedHttpResponse<?> streamedResponse) {
                        DelayedExecutionFlow<HttpResponse<?>> delayed = DelayedExecutionFlow.create();
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
                                completeExceptionallySafe(delayed, t);
                            }

                            @Override
                            public void onComplete() {
                                try {
                                    FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(nettyResponse.protocolVersion(), nettyResponse.status(), buffer, nettyResponse.headers(), new DefaultHttpHeaders(true));
                                    final FullNettyClientHttpResponse<Object> fullNettyClientHttpResponse = new FullNettyClientHttpResponse<>(fullHttpResponse, handlerRegistry, (Argument<Object>) errorType, true, conversionService);
                                    completeExceptionallySafe(delayed, decorate(new HttpClientResponseException(
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
                        return delayed;
                    }
                }
                return ExecutionFlow.error(clientException);
            });
        }
        return publisher;
    }

    private <I> ExecutionFlow<URI> resolveURI(io.micronaut.http.HttpRequest<I> request, boolean includeContextPath) {
        URI requestURI = request.getUri();
        if (loadBalancer == null) {
            return ExecutionFlow.error(decorate(new NoHostException("Request URI specifies no host to connect to")));
        }
        ExecutionFlow<ServiceInstance> selected;
        if (loadBalancer instanceof FixedLoadBalancer fixed) {
            selected = ExecutionFlow.just(fixed.getServiceInstance());
        } else {
            selected = ReactiveExecutionFlow.fromPublisher(loadBalancer.select(getLoadBalancerDiscriminator()));
        }

        return selected.map(server -> {
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

    private <R extends HttpResponse<?>> ExecutionFlow<R> handleStreamHttpError(
            R response,
            boolean failOnError
    ) {
        boolean errorStatus = response.code() >= 400;
        if (errorStatus && failOnError) {
            // todo: close response properly
            return ExecutionFlow.error(decorate(new HttpClientResponseException(response.reason(), response)));
        } else {
            return ExecutionFlow.just(response);
        }
    }

    @Override
    public Publisher<? extends HttpResponse<?>> exchange(io.micronaut.http.HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable Thread blockedThread) {
        if (requestBody == null) {
            requestBody = AvailableNettyByteBody.empty();
        }
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        ExecutionFlow<HttpResponse<?>> mono = null;
        try {
            mono = sendRequestWithRedirects(
                propagatedContext,
                blockedThread == null ? null : new BlockHint(blockedThread, null),
                new RawHttpRequestWrapper<>(conversionService, request.toMutableRequest(), requestBody),
                (req, resp) -> ExecutionFlow.just(resp)
            );
        } finally {
            if (mono == null) {
                requestBody.close();
            }
        }
        return toMono(mono, propagatedContext).doOnTerminate(requestBody::close);
    }

    private ExecutionFlow<HttpResponse<?>> sendRequestWithRedirects(
        PropagatedContext propagatedContext,
        @Nullable BlockHint blockHint,
        MutableHttpRequest<?> request,
        BiFunction<MutableHttpRequest<?>, NettyClientByteBodyResponse, ? extends ExecutionFlow<? extends HttpResponse<?>>> readResponse
    ) {
        return sendRequestWithRedirects(propagatedContext, new AtomicReference<>(), blockHint, request, readResponse);
    }

    /**
     * This is the high-level request method. It sits above {@link #sendRawRequest} and handles
     * things like filters, error handling, response parsing, request writing.
     *
     * @param propagatedContext The context propagated from the original client call
     * @param preferredScheduler A reference holding the preferred scheduler for timeouts. This is
     *                           replaced by the connection event loop ASAP so that callers can take
     *                           advantage of locality
     * @param blockHint         The optional block hint
     * @param request           The request to send. Must have resolved absolute URI (see {@link #resolveURI})
     * @param readResponse      Function that reads the response from the raw
     *                          {@link NettyClientByteBodyResponse} representation. This is run exactly
     *                          once, but if there is a redirect, it potentially runs with a different
     *                          request than the original (which is why it has a request parameter)
     * @return A mono containing the response
     */
    private ExecutionFlow<HttpResponse<?>> sendRequestWithRedirects(
        PropagatedContext propagatedContext,
        AtomicReference<ScheduledExecutorService> preferredScheduler,
        @Nullable BlockHint blockHint,
        MutableHttpRequest<?> request,
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
                    try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                        return sendRequestWithRedirectsNoFilter(
                            propagatedContext,
                            preferredScheduler,
                            blockHint,
                            MutableHttpRequestWrapper.wrapIfNecessary(conversionService, request),
                            readResponse
                        );
                    }
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
            .flatMap(poolHandle -> {
                poolHandle.touch();
                preferredScheduler.set(poolHandle.channel.eventLoop());

                // build the raw request
                request.setAttribute(NettyClientHttpRequest.CHANNEL, poolHandle.channel);

                URI requestURI = request.getUri();
                boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod());
                NettyByteBody byteBody;
                try {
                    byteBody = buildNettyRequest(
                        request,
                        requestURI,
                        request
                            .getContentType()
                            .orElse(MediaType.APPLICATION_JSON_TYPE),
                        permitsBody,
                        poolHandle.channel.eventLoop()
                    );
                } catch (HttpPostRequestEncoder.ErrorDataEncoderException e) {
                    poolHandle.release();
                    return ExecutionFlow.error(e);
                }

                // send the raw request
                return sendRawRequest(poolHandle, request, byteBody);
            })
            .flatMap(byteBodyResponse -> {
                // handle redirects or map the response bytes

                int code = byteBodyResponse.code();
                HttpHeaders nettyHeaders = byteBodyResponse.getHeaders().getNettyHeaders();
                if (code > 300 && code < 400 && configuration.isFollowRedirects() && nettyHeaders.contains(HttpHeaderNames.LOCATION)) {
                    byteBodyResponse.close();
                    String location = nettyHeaders.get(HttpHeaderNames.LOCATION);

                    MutableHttpRequest<Object> redirectRequest;
                    if (code == 307 || code == 308) {
                        redirectRequest = io.micronaut.http.HttpRequest.create(request.getMethod(), location);
                        request.getBody().ifPresent(redirectRequest::body);
                    } else {
                        redirectRequest = io.micronaut.http.HttpRequest.GET(location);
                    }

                    setRedirectHeaders(request, redirectRequest);
                    return resolveRedirectURI(request, redirectRequest)
                        .flatMap(uri -> sendRequestWithRedirects(propagatedContext, blockHint, redirectRequest.uri(uri), readResponse));
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
     * This is the low-level request method, without redirect handling and with raw body bytes.
     *
     * @param poolHandle         The pool handle to send the request on
     * @param request            The request to send
     * @param byteBody           The request body
     * @return A mono containing the response
     */
    private ExecutionFlow<NettyClientByteBodyResponse> sendRawRequest(
        ConnectionManager.PoolHandle poolHandle,
        io.micronaut.http.HttpRequest<?> request,
        NettyByteBody byteBody
    ) {
        poolHandle.touch();
        URI uri = request.getUri();
        String uriWithoutHost = uri.getRawPath();
        if (uri.getRawQuery() != null) {
            uriWithoutHost += "?" + uri.getRawQuery();
        }
        HttpRequest nettyRequest = NettyHttpRequestBuilder.asBuilder(request)
            .toHttpRequestWithoutBody()
            .setUri(uriWithoutHost);

        DelayedExecutionFlow<NettyClientByteBodyResponse> flow = DelayedExecutionFlow.create();
        // need to run the create() on the event loop so that pipeline modification happens synchronously
        if (poolHandle.channel.eventLoop().inEventLoop()) {
            sendRawRequest0(poolHandle, request, byteBody, flow, nettyRequest);
        } else {
            poolHandle.channel.eventLoop().execute(() -> sendRawRequest0(poolHandle, request, byteBody, flow, nettyRequest));
        }
        return flow;
    }

    private void sendRawRequest0(ConnectionManager.PoolHandle poolHandle, io.micronaut.http.HttpRequest<?> request, NettyByteBody byteBody, DelayedExecutionFlow<NettyClientByteBodyResponse> sink, HttpRequest nettyRequest) {
        if (log.isDebugEnabled()) {
            log.debug("Sending HTTP {} to {}", request.getMethodName(), request.getUri());
        }

        boolean expectContinue = HttpUtil.is100ContinueExpected(nettyRequest);
        ChannelPipeline pipeline = poolHandle.channel.pipeline();

        // if the body is streamed, we have a StreamWriter, otherwise we have a ByteBuf.
        StreamWriter streamWriter;
        ByteBuf byteBuf;
        if (byteBody instanceof AvailableNettyByteBody available) {
            byteBuf = AvailableNettyByteBody.toByteBuf(available);
            streamWriter = null;
        } else {
            streamWriter = new StreamWriter((StreamingNettyByteBody) byteBody, e -> {
                poolHandle.taint();
                completeExceptionallySafe(sink, e);
            });
            pipeline.addLast(streamWriter);
            byteBuf = null;
        }

        if (log.isTraceEnabled()) {
            HttpHeadersUtil.trace(log, nettyRequest.headers().names(), nettyRequest.headers()::getAll);
            if (byteBuf != null) {
                traceBody("Request", byteBuf);
            }
        }

        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE, new Http1ResponseHandler(new Http1ResponseHandler.ResponseListener() {
            boolean stillExpectingContinue = expectContinue;

            @Override
            public void fail(ChannelHandlerContext ctx, Throwable cause) {
                poolHandle.taint();
                completeExceptionallySafe(sink, handleResponseError(request, cause));
            }

            @Override
            public void continueReceived(ChannelHandlerContext ctx) {
                if (stillExpectingContinue) {
                    stillExpectingContinue = false;
                    if (streamWriter == null) {
                        ctx.writeAndFlush(new DefaultLastHttpContent(byteBuf), ctx.voidPromise());
                    } else {
                        streamWriter.startWriting();
                    }
                }
            }

            @Override
            public void complete(io.netty.handler.codec.http.HttpResponse response, CloseableByteBody body) {
                if (!HttpUtil.isKeepAlive(response)) {
                    poolHandle.taint();
                }

                sink.complete(new NettyClientByteBodyResponse(response, body, conversionService));
            }

            @Override
            public BodySizeLimits sizeLimits() {
                return DefaultHttpClient.this.sizeLimits();
            }

            @Override
            public boolean isHeadResponse() {
                return nettyRequest.method().equals(HttpMethod.HEAD);
            }

            @Override
            public void finish(ChannelHandlerContext ctx) {
                ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE);
                if (streamWriter != null) {
                    if (!streamWriter.isCompleted()) {
                        // if there was an error, and we didn't fully write the request yet, the
                        // connection cannot be reused
                        poolHandle.taint();
                    }
                    ctx.pipeline().remove(streamWriter);
                }
                if (stillExpectingContinue && byteBuf != null) {
                    byteBuf.release();
                }
                poolHandle.release();
            }
        }));
        poolHandle.notifyRequestPipelineBuilt();

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

        if (!poolHandle.http2) {
            if (poolHandle.canReturn()) {
                nettyRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                nettyRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
        }

        Channel channel = poolHandle.channel();
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
                ), channel.voidPromise());
            } else {
                channel.writeAndFlush(nettyRequest, channel.voidPromise());
            }
        } else {
            channel.writeAndFlush(nettyRequest, channel.voidPromise());
            if (!expectContinue) {
                streamWriter.startWriting();
            }
        }
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
            return NettyBodyAdapter.adapt(bytes, eventLoop);
        } else {
            return new AvailableNettyByteBody(((FullHttpRequest) finalized).content());
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

    private @NonNull HttpClientException handleResponseError(io.micronaut.http.HttpRequest<?> finalRequest, Throwable cause) {
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
            result = decorate(new ContentLengthExceededException(clee.getMessage()));
        } else if (cause instanceof io.micronaut.http.exceptions.BufferLengthExceededException blee) {
            result = decorate(new ContentLengthExceededException(blee.getAdvertisedLength(), blee.getReceivedLength()));
        } else if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
            result = ReadTimeoutException.TIMEOUT_EXCEPTION;
        } else if (cause instanceof HttpClientException hce) {
            result = decorate(hce);
        } else {
            result = decorate(new HttpClientException("Error occurred reading HTTP response: " + message, cause));
        }
        return result;
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

    private BodySizeLimits sizeLimits() {
        return new BodySizeLimits(Long.MAX_VALUE, configuration.getMaxContentLength());
    }

    private static <O, E> boolean shouldConvertWithBodyType(io.netty.handler.codec.http.HttpResponse msg,
                                                            HttpClientConfiguration configuration,
                                                            Argument<O> bodyType,
                                                            Argument<E> errorType) {
        if (msg.status().code() < 400) {
            return true;
        }
        return !configuration.isExceptionOnErrorStatus() && bodyType.equalsType(errorType);
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
    private HttpClientResponseException makeErrorFromRequestBody(Argument<?> errorType, HttpResponseStatus status, FullNettyClientHttpResponse<?> response) {
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

    /**
     * Used as a holder for the current SSE event.
     */
    private static final class CurrentEvent {
        byte[] data;
        String id;
        String name;
        Duration retry;
    }
}
