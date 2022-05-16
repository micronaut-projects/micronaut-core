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
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientErrorDecoder;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.filter.DefaultHttpClientFilterResolver;
import io.micronaut.http.client.filters.ClientServerContextFilter;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.multipart.MultipartDataFactory;
import io.micronaut.http.client.netty.ssl.NettyClientSslBuilder;
import io.micronaut.http.client.netty.websocket.NettyWebSocketClientHandler;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpClientFilterResolver;
import io.micronaut.http.filter.HttpFilterResolver;
import io.micronaut.http.multipart.MultipartException;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.netty.stream.DefaultHttp2Content;
import io.micronaut.http.netty.stream.DefaultStreamedHttpResponse;
import io.micronaut.http.netty.stream.Http2Content;
import io.micronaut.http.netty.stream.HttpStreamsClientHandler;
import io.micronaut.http.netty.stream.JsonSubscriber;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.netty.stream.StreamingInboundHttp2ToHttpAdapter;
import io.micronaut.http.sse.Event;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.jackson.databind.JacksonDatabindMapper;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.codec.MapperMediaTypeCodec;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import io.micronaut.json.codec.JsonStreamMediaTypeCodec;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.instrument.Instrumentation;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.micronaut.http.client.HttpClientConfiguration.DEFAULT_SHUTDOWN_QUIET_PERIOD_MILLISECONDS;
import static io.micronaut.http.client.HttpClientConfiguration.DEFAULT_SHUTDOWN_TIMEOUT_MILLISECONDS;
import static io.micronaut.http.netty.channel.ChannelPipelineCustomizer.HANDLER_HTTP2_SETTINGS;
import static io.micronaut.http.netty.channel.ChannelPipelineCustomizer.HANDLER_IDLE_STATE;
import static io.micronaut.scheduling.instrument.InvocationInstrumenter.NOOP;

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
    private static final AttributeKey<Http2Stream> STREAM_KEY = AttributeKey.valueOf("micronaut.http2.stream");
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    /**
     * Which headers <i>not</i> to copy from the first request when redirecting to a second request. There doesn't
     * appear to be a spec for this. {@link java.net.HttpURLConnection} seems to drop all headers, but that would be a
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

    protected final Bootstrap bootstrap;
    protected EventLoopGroup group;
    protected MediaTypeCodecRegistry mediaTypeCodecRegistry;
    protected ByteBufferFactory<ByteBufAllocator, ByteBuf> byteBufferFactory = new NettyByteBufferFactory();

    private final List<HttpFilterResolver.FilterEntry<HttpClientFilter>> clientFilterEntries;
    private final io.micronaut.http.HttpVersion httpVersion;
    private final Scheduler scheduler;
    private final LoadBalancer loadBalancer;
    private final HttpClientConfiguration configuration;
    private final String contextPath;
    private final SslContext sslContext;
    private final ThreadFactory threadFactory;
    private final boolean shutdownGroup;
    private final Charset defaultCharset;
    private final ChannelPoolMap<RequestKey, ChannelPool> poolMap;
    private final Logger log;
    private final @Nullable
    Long readTimeoutMillis;
    private final @Nullable
    Long connectionTimeAliveMillis;
    private final HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver;
    private final WebSocketBeanRegistry webSocketRegistry;
    private final RequestBinderRegistry requestBinderRegistry;
    private final Collection<ChannelPipelineListener> pipelineListeners;
    private final List<InvocationInstrumenterFactory> invocationInstrumenterFactories;

    /**
     * Construct a client for the given arguments.
     *
     * @param loadBalancer                    The {@link LoadBalancer} to use for selecting servers
     * @param configuration                   The {@link HttpClientConfiguration} object
     * @param contextPath                     The base URI to prepend to request uris
     * @param threadFactory                   The thread factory to use for client threads
     * @param nettyClientSslBuilder           The SSL builder
     * @param codecRegistry                   The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param annotationMetadataResolver      The annotation metadata resolver
     * @param invocationInstrumenterFactories The invocation instrumeter factories to instrument netty handlers execution with
     * @param filters                         The filters to use
     */
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer,
            @NonNull HttpClientConfiguration configuration,
            @Nullable String contextPath,
            @Nullable ThreadFactory threadFactory,
            NettyClientSslBuilder nettyClientSslBuilder,
            MediaTypeCodecRegistry codecRegistry,
            @Nullable AnnotationMetadataResolver annotationMetadataResolver,
            List<InvocationInstrumenterFactory> invocationInstrumenterFactories,
            HttpClientFilter... filters) {
        this(loadBalancer, configuration.getHttpVersion(), configuration, contextPath, new DefaultHttpClientFilterResolver(annotationMetadataResolver, Arrays.asList(filters)), null, threadFactory, nettyClientSslBuilder, codecRegistry, WebSocketBeanRegistry.EMPTY, new DefaultRequestBinderRegistry(ConversionService.SHARED), null, NioSocketChannel::new, Collections.emptySet(), invocationInstrumenterFactories);
    }

    /**
     * Construct a client for the given arguments.
     *  @param loadBalancer                    The {@link LoadBalancer} to use for selecting servers
     * @param httpVersion                     The HTTP version to use. Can be null and defaults to {@link io.micronaut.http.HttpVersion#HTTP_1_1}
     * @param configuration                   The {@link HttpClientConfiguration} object
     * @param contextPath                     The base URI to prepend to request uris
     * @param filterResolver                  The http client filter resolver
     * @param clientFilterEntries             The client filter entries
     * @param threadFactory                   The thread factory to use for client threads
     * @param nettyClientSslBuilder           The SSL builder
     * @param codecRegistry                   The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param webSocketBeanRegistry           The websocket bean registry
     * @param requestBinderRegistry           The request binder registry
     * @param eventLoopGroup                  The event loop group to use
     * @param socketChannelFactory            The socket channel factory
     * @param pipelineListeners               The listeners to call for pipeline customization
     * @param invocationInstrumenterFactories The invocation instrumeter factories to instrument netty handlers execution with
     */
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer,
                             @Nullable io.micronaut.http.HttpVersion httpVersion,
                             @NonNull HttpClientConfiguration configuration,
                             @Nullable String contextPath,
                             @NonNull HttpClientFilterResolver<ClientFilterResolutionContext> filterResolver,
                             List<HttpFilterResolver.FilterEntry<HttpClientFilter>> clientFilterEntries,
                             @Nullable ThreadFactory threadFactory,
                             @NonNull NettyClientSslBuilder nettyClientSslBuilder,
                             @NonNull MediaTypeCodecRegistry codecRegistry,
                             @NonNull WebSocketBeanRegistry webSocketBeanRegistry,
                             @NonNull RequestBinderRegistry requestBinderRegistry,
                             @Nullable EventLoopGroup eventLoopGroup,
                             @NonNull ChannelFactory socketChannelFactory,
                             Collection<ChannelPipelineListener> pipelineListeners,
                             List<InvocationInstrumenterFactory> invocationInstrumenterFactories
    ) {
        ArgumentUtils.requireNonNull("nettyClientSslBuilder", nettyClientSslBuilder);
        ArgumentUtils.requireNonNull("codecRegistry", codecRegistry);
        ArgumentUtils.requireNonNull("webSocketBeanRegistry", webSocketBeanRegistry);
        ArgumentUtils.requireNonNull("requestBinderRegistry", requestBinderRegistry);
        ArgumentUtils.requireNonNull("configuration", configuration);
        ArgumentUtils.requireNonNull("filterResolver", filterResolver);
        ArgumentUtils.requireNonNull("socketChannelFactory", socketChannelFactory);
        this.loadBalancer = loadBalancer;
        this.httpVersion = httpVersion != null ? httpVersion : configuration.getHttpVersion();
        this.defaultCharset = configuration.getDefaultCharset();
        if (StringUtils.isNotEmpty(contextPath)) {
            if (contextPath.charAt(0) != '/') {
                contextPath = '/' + contextPath;
            }
            this.contextPath = contextPath;
        } else {
            this.contextPath = null;
        }
        this.bootstrap = new Bootstrap();
        this.configuration = configuration;
        this.sslContext = nettyClientSslBuilder.build(configuration.getSslConfiguration(), this.httpVersion).orElse(null);
        if (eventLoopGroup != null) {
            this.group = eventLoopGroup;
            this.shutdownGroup = false;
        } else {
            this.group = createEventLoopGroup(configuration, threadFactory);
            this.shutdownGroup = true;
        }

        this.scheduler = Schedulers.fromExecutorService(group);
        this.threadFactory = threadFactory;
        this.bootstrap.group(group)
                .channelFactory(socketChannelFactory)
                .option(ChannelOption.SO_KEEPALIVE, true);

        Optional<Duration> readTimeout = configuration.getReadTimeout();
        this.readTimeoutMillis = readTimeout.map(duration -> !duration.isNegative() ? duration.toMillis() : null).orElse(null);

        Optional<Duration> connectTtl = configuration.getConnectTtl();
        this.connectionTimeAliveMillis = connectTtl.map(duration -> !duration.isNegative() ? duration.toMillis() : null).orElse(null);

        this.invocationInstrumenterFactories =
                invocationInstrumenterFactories == null ? Collections.emptyList() : invocationInstrumenterFactories;

        HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration = configuration.getConnectionPoolConfiguration();
        // HTTP/2 defaults to keep alive connections so should we should always use a pool
        if (connectionPoolConfiguration.isEnabled() || this.httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {
            int maxConnections = connectionPoolConfiguration.getMaxConnections();
            if (maxConnections > -1) {
                poolMap = new AbstractChannelPoolMap<RequestKey, ChannelPool>() {
                    @Override
                    protected ChannelPool newPool(RequestKey key) {
                        Bootstrap newBootstrap = bootstrap.clone(group);
                        initBootstrapForProxy(newBootstrap, key.isSecure(), key.getHost(), key.getPort());
                        newBootstrap.remoteAddress(key.getRemoteAddress());

                        AbstractChannelPoolHandler channelPoolHandler = newPoolHandler(key);
                        final long acquireTimeoutMillis = connectionPoolConfiguration.getAcquireTimeout().map(Duration::toMillis).orElse(-1L);
                        return new FixedChannelPool(
                                newBootstrap,
                                channelPoolHandler,
                                ChannelHealthChecker.ACTIVE,
                                acquireTimeoutMillis > -1 ? FixedChannelPool.AcquireTimeoutAction.FAIL : null,
                                acquireTimeoutMillis,
                                maxConnections,
                                connectionPoolConfiguration.getMaxPendingAcquires()

                        );
                    }
                };
            } else {
                poolMap = new AbstractChannelPoolMap<RequestKey, ChannelPool>() {
                    @Override
                    protected ChannelPool newPool(RequestKey key) {
                        Bootstrap newBootstrap = bootstrap.clone(group);
                        initBootstrapForProxy(newBootstrap, key.isSecure(), key.getHost(), key.getPort());
                        newBootstrap.remoteAddress(key.getRemoteAddress());

                        AbstractChannelPoolHandler channelPoolHandler = newPoolHandler(key);
                        return new SimpleChannelPool(
                                newBootstrap,
                                channelPoolHandler
                        );
                    }
                };
            }
        } else {
            this.poolMap = null;
        }

        Optional<Duration> connectTimeout = configuration.getConnectTimeout();
        connectTimeout.ifPresent(duration -> this.bootstrap.option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                (int) duration.toMillis()
        ));

        for (Map.Entry<String, Object> entry : configuration.getChannelOptions().entrySet()) {
            Object v = entry.getValue();
            if (v != null) {
                String channelOption = entry.getKey();
                bootstrap.option(ChannelOption.valueOf(channelOption), v);
            }
        }
        this.mediaTypeCodecRegistry = codecRegistry;
        this.log = configuration.getLoggerName().map(LoggerFactory::getLogger).orElse(DEFAULT_LOG);
        this.filterResolver = filterResolver;
        if (clientFilterEntries != null) {
            this.clientFilterEntries = clientFilterEntries;
        } else {
            this.clientFilterEntries = filterResolver.resolveFilterEntries(
                    new ClientFilterResolutionContext(null, AnnotationMetadata.EMPTY_METADATA)
            );
        }
        this.webSocketRegistry = webSocketBeanRegistry != null ? webSocketBeanRegistry : WebSocketBeanRegistry.EMPTY;
        this.requestBinderRegistry = requestBinderRegistry;
        this.pipelineListeners = pipelineListeners;
    }

    /**
     * @param uri The URL
     */
    public DefaultHttpClient(@Nullable URI uri) {
        this(uri, new DefaultHttpClientConfiguration());
    }

    /**
     *
     */
    public DefaultHttpClient() {
        this(null, new DefaultHttpClientConfiguration(), Collections.emptyList());
    }

    /**
     * @param uri           The URI
     * @param configuration The {@link HttpClientConfiguration} object
     */
    public DefaultHttpClient(@Nullable URI uri, @NonNull HttpClientConfiguration configuration) {
        this(
                uri == null ? null : LoadBalancer.fixed(uri), configuration, null, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
                new NettyClientSslBuilder(new ResourceResolver()),
                createDefaultMediaTypeRegistry(),
                AnnotationMetadataResolver.DEFAULT,
                Collections.emptyList());
    }

    /**
     * @param loadBalancer  The {@link LoadBalancer} to use for selecting servers
     * @param configuration The {@link HttpClientConfiguration} object
     * @param invocationInstrumenterFactories The invocation instrumeter factories to instrument netty handlers execution with
     */
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer, HttpClientConfiguration configuration, List<InvocationInstrumenterFactory> invocationInstrumenterFactories) {
        this(loadBalancer,
                configuration, null, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
                new NettyClientSslBuilder(new ResourceResolver()),
                createDefaultMediaTypeRegistry(),
                AnnotationMetadataResolver.DEFAULT,
                invocationInstrumenterFactories);
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

    @Override
    public HttpClient start() {
        if (!isRunning()) {
            this.group = createEventLoopGroup(configuration, threadFactory);
        }
        return this;
    }

    @Override
    public boolean isRunning() {
        return !group.isShutdown();
    }

    @Override
    public HttpClient stop() {
        if (isRunning()) {
            if (poolMap instanceof Iterable) {
                Iterable<Map.Entry<RequestKey, ChannelPool>> i = (Iterable) poolMap;
                for (Map.Entry<RequestKey, ChannelPool> entry : i) {
                    ChannelPool cp = entry.getValue();
                    try {
                        if (cp instanceof SimpleChannelPool) {
                            addInstrumentedListener(((SimpleChannelPool) cp).closeAsync(), future -> {
                                if (!future.isSuccess()) {
                                    final Throwable cause = future.cause();
                                    if (cause != null) {
                                        log.error("Error shutting down HTTP client connection pool: " + cause.getMessage(), cause);
                                    }
                                }
                            });
                        } else {
                            cp.close();
                        }
                    } catch (Exception cause) {
                        log.error("Error shutting down HTTP client connection pool: " + cause.getMessage(), cause);
                    }

                }
            }
            if (shutdownGroup) {
                Duration shutdownTimeout = configuration.getShutdownTimeout()
                    .orElse(Duration.ofMillis(DEFAULT_SHUTDOWN_TIMEOUT_MILLISECONDS));
                Duration shutdownQuietPeriod = configuration.getShutdownQuietPeriod()
                    .orElse(Duration.ofMillis(DEFAULT_SHUTDOWN_QUIET_PERIOD_MILLISECONDS));

                Future<?> future = this.group.shutdownGracefully(
                        shutdownQuietPeriod.toMillis(),
                        shutdownTimeout.toMillis(),
                        TimeUnit.MILLISECONDS
                );
                addInstrumentedListener(future, f -> {
                    if (!f.isSuccess() && log.isErrorEnabled()) {
                        Throwable cause = f.cause();
                        log.error("Error shutting down HTTP client: " + cause.getMessage(), cause);
                    }
                });
                try {
                    future.await(shutdownTimeout.toMillis());
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        return this;
    }

    /**
     * @return The {@link MediaTypeCodecRegistry} used by this client
     */
    public MediaTypeCodecRegistry getMediaTypeCodecRegistry() {
        return mediaTypeCodecRegistry;
    }

    /**
     * Sets the {@link MediaTypeCodecRegistry} used by this client.
     *
     * @param mediaTypeCodecRegistry The registry to use. Should not be null
     */
    public void setMediaTypeCodecRegistry(MediaTypeCodecRegistry mediaTypeCodecRegistry) {
        if (mediaTypeCodecRegistry != null) {
            this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        }
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return new BlockingHttpClient() {

            @Override
            public void close() {
                DefaultHttpClient.this.close();
            }

            @Override
            public <I, O, E> io.micronaut.http.HttpResponse<O> exchange(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
                Flux<HttpResponse<O>> publisher = Flux.from(DefaultHttpClient.this.exchange(request, bodyType, errorType));
                return publisher.doOnNext(res -> {
                    Optional<ByteBuf> byteBuf = res.getBody(ByteBuf.class);
                    byteBuf.ifPresent(bb -> {
                        if (bb.refCnt() > 0) {
                            ReferenceCountUtil.safeRelease(bb);
                        }
                    });
                    if (res instanceof FullNettyClientHttpResponse) {
                        ((FullNettyClientHttpResponse) res).onComplete();
                    }
                }).blockFirst();
            }
        };
    }

    @SuppressWarnings("SubscriberImplementation")
    @Override
    public <I> Publisher<Event<ByteBuffer<?>>> eventStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return eventStreamOrError(request, null);
    }

    private <I> Publisher<Event<ByteBuffer<?>>> eventStreamOrError(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {

        if (request instanceof MutableHttpRequest) {
            ((MutableHttpRequest) request).accept(MediaType.TEXT_EVENT_STREAM_TYPE);
        }

        return Flux.create(emitter ->
                dataStream(request, errorType).subscribe(new Subscriber<ByteBuffer<?>>() {
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
                                            case "data":
                                                ByteBuffer content = buffer.slice(fromIndex, toIndex);
                                                byte[] d = currentEvent.data;
                                                if (d == null) {
                                                    currentEvent.data = content.toByteArray();
                                                } else {
                                                    currentEvent.data = ArrayUtils.concat(d, content.toByteArray());
                                                }


                                                break;
                                            case "id":
                                                ByteBuffer id = buffer.slice(fromIndex, toIndex);
                                                currentEvent.id = id.toString(StandardCharsets.UTF_8).trim();

                                                break;
                                            case "event":
                                                ByteBuffer event = buffer.slice(fromIndex, toIndex);
                                                currentEvent.name = event.toString(StandardCharsets.UTF_8).trim();

                                                break;
                                            case "retry":
                                                ByteBuffer retry = buffer.slice(fromIndex, toIndex);
                                                String text = retry.toString(StandardCharsets.UTF_8);
                                                if (!StringUtils.isEmpty(text)) {
                                                    Long millis = Long.valueOf(text);
                                                    currentEvent.retry = Duration.ofMillis(millis);
                                                }

                                                break;
                                            default:
                                                // ignore message
                                                break;
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
                            if (buffer instanceof ReferenceCounted) {
                                ((ReferenceCounted) buffer).release();
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        dataSubscription.cancel();
                        if (t instanceof HttpClientException) {
                            emitter.error(t);
                        } else {
                            emitter.error(new HttpClientException("Error consuming Server Sent Events: " + t.getMessage(), t));
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
        return eventStream(request, eventType, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, B> Publisher<Event<B>> eventStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<B> eventType, @NonNull Argument<?> errorType) {
        return Flux.from(eventStreamOrError(request, errorType)).map(byteBufferEvent -> {
            ByteBuffer<?> data = byteBufferEvent.getData();
            Optional<MediaTypeCodec> registeredCodec;

            if (mediaTypeCodecRegistry != null) {
                registeredCodec = mediaTypeCodecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE);
            } else {
                registeredCodec = Optional.empty();
            }

            if (registeredCodec.isPresent()) {
                B decoded = registeredCodec.get().decode(eventType, data);
                return Event.of(byteBufferEvent, decoded);
            } else {
                throw new CodecException("JSON codec not present");
            }
        });
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return dataStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<ByteBuffer<?>> dataStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return new MicronautFlux<>(Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> dataStreamImpl(request, errorType, parentRequest, requestURI)))
                .doAfterNext(buffer -> {
                    Object o = buffer.asNativeBuffer();
                    if (o instanceof ByteBuf) {
                        ByteBuf byteBuf = (ByteBuf) o;
                        if (byteBuf.refCnt() > 0) {
                            ReferenceCountUtil.safeRelease(byteBuf);
                        }
                    }
                });
    }

    @Override
    public <I> Publisher<io.micronaut.http.HttpResponse<ByteBuffer<?>>> exchangeStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return exchangeStream(request, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I> Publisher<io.micronaut.http.HttpResponse<ByteBuffer<?>>> exchangeStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<?> errorType) {
        io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return new MicronautFlux<>(Flux.from(resolveRequestURI(request))
                .flatMap(uri -> exchangeStreamImpl(parentRequest, request, errorType, uri)))
                .doAfterNext(byteBufferHttpResponse -> {
                    ByteBuffer<?> buffer = byteBufferHttpResponse.body();
                    if (buffer instanceof ReferenceCounted) {
                        ((ReferenceCounted) buffer).release();
                    }
                });
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> type) {
        return jsonStream(request, type, DEFAULT_ERROR_TYPE);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> type, @NonNull Argument<?> errorType) {
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> jsonStreamImpl(parentRequest, request, type, errorType, requestURI));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I> Publisher<Map<String, Object>> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request) {
        return (Publisher) jsonStream(request, Map.class);
    }

    @Override
    public <I, O> Publisher<O> jsonStream(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Class<O> type) {
        return jsonStream(request, io.micronaut.core.type.Argument.of(type));
    }

    @Override
    public <I, O, E> Publisher<io.micronaut.http.HttpResponse<O>> exchange(@NonNull io.micronaut.http.HttpRequest<I> request, @NonNull Argument<O> bodyType, @NonNull Argument<E> errorType) {
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        Publisher<URI> uriPublisher = resolveRequestURI(request);
        return Flux.from(uriPublisher)
                .switchMap(uri -> exchangeImpl(uri, parentRequest, request, bodyType, errorType));
    }

    @Override
    public <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, io.micronaut.http.MutableHttpRequest<?> request) {
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

    private <T> Flux<T> connectWebSocket(URI uri, MutableHttpRequest<?> request, Class<T> clientEndpointType, WebSocketBean<T> webSocketBean) {
        Bootstrap bootstrap = this.bootstrap.clone();
        if (webSocketBean == null) {
            webSocketBean = webSocketRegistry.getWebSocket(clientEndpointType);
        }

        WebSocketBean<T> finalWebSocketBean = webSocketBean;
        return Flux.create(emitter -> {
            SslContext sslContext = buildSslContext(uri);
            WebSocketVersion protocolVersion = finalWebSocketBean.getBeanDefinition().enumValue(ClientWebSocket.class, "version", WebSocketVersion.class).orElse(WebSocketVersion.V13);
            int maxFramePayloadLength = finalWebSocketBean.messageMethod()
                    .map(m -> m.intValue(OnMessage.class, "maxPayloadLength")
                            .orElse(65536)).orElse(65536);
            String subprotocol = finalWebSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class, "subprotocol").orElse(StringUtils.EMPTY_STRING);

            RequestKey requestKey;
            try {
                requestKey = new RequestKey(uri);
            } catch (HttpClientException e) {
                emitter.error(e);
                return;
            }

            bootstrap.remoteAddress(requestKey.getHost(), requestKey.getPort());
            initBootstrapForProxy(bootstrap, sslContext != null, requestKey.getHost(), requestKey.getPort());
            bootstrap.handler(new HttpClientInitializer(
                    sslContext,
                    requestKey.getHost(),
                    requestKey.getPort(),
                    false,
                    false,
                    false,
                    null
            ) {
                @Override
                protected void addFinalHandler(ChannelPipeline pipeline) {
                    pipeline.remove(ChannelPipelineCustomizer.HANDLER_HTTP_DECODER);
                    ReadTimeoutHandler readTimeoutHandler = pipeline.get(ReadTimeoutHandler.class);
                    if (readTimeoutHandler != null) {
                        pipeline.remove(readTimeoutHandler);
                    }

                    Optional<Duration> readIdleTime = configuration.getReadIdleTimeout();
                    if (readIdleTime.isPresent()) {
                        Duration duration = readIdleTime.get();
                        if (!duration.isNegative()) {
                            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_IDLE_STATE, new IdleStateHandler(duration.toMillis(), duration.toMillis(), duration.toMillis(), TimeUnit.MILLISECONDS));
                        }
                    }

                    final NettyWebSocketClientHandler webSocketHandler;
                    try {
                        String scheme =  (sslContext == null) ? "ws" : "wss";
                        URI webSocketURL = UriBuilder.of(uri)
                                .scheme(scheme)
                                .host(host)
                                .port(port)
                                .build();

                        MutableHttpHeaders headers = request.getHeaders();
                        HttpHeaders customHeaders = EmptyHttpHeaders.INSTANCE;
                        if (headers instanceof NettyHttpHeaders) {
                            customHeaders = ((NettyHttpHeaders) headers).getNettyHeaders();
                        }
                        if (StringUtils.isNotEmpty(subprotocol)) {
                            customHeaders.add("Sec-WebSocket-Protocol", subprotocol);
                        }

                        webSocketHandler = new NettyWebSocketClientHandler<>(
                                request,
                                finalWebSocketBean,
                                WebSocketClientHandshakerFactory.newHandshaker(
                                        webSocketURL, protocolVersion, subprotocol, true, customHeaders, maxFramePayloadLength),
                                requestBinderRegistry,
                                mediaTypeCodecRegistry,
                                emitter);
                        pipeline.addLast(WebSocketClientCompressionHandler.INSTANCE);
                        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_WEBSOCKET_CLIENT, webSocketHandler);
                    } catch (Throwable e) {
                        emitter.error(new WebSocketSessionException("Error opening WebSocket client session: " + e.getMessage(), e));
                    }
                }
            });

            addInstrumentedListener(bootstrap.connect(), future -> {
                if (!future.isSuccess()) {
                    emitter.error(future.cause());
                }
            });
        }, FluxSink.OverflowStrategy.ERROR);
    }

    private <I> Flux<HttpResponse<ByteBuffer<?>>> exchangeStreamImpl(io.micronaut.http.HttpRequest<Object> parentRequest, io.micronaut.http.HttpRequest<I> request, Argument<?> errorType, URI requestURI) {
        Flux<HttpResponse<Object>> streamResponsePublisher = Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
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
                        NettyStreamedHttpResponse<ByteBuffer<?>> thisResponse = new NettyStreamedHttpResponse<>(
                                streamedHttpResponse,
                                response.status()
                        );
                        thisResponse.setBody(byteBuffer);
                        return (HttpResponse<ByteBuffer<?>>) new HttpResponseWrapper<>(thisResponse);
                    });
        }).doOnTerminate(() -> {
            final Object o = request.getAttribute(NettyClientHttpRequest.CHANNEL).orElse(null);
            if (o instanceof Channel) {
                final Channel c = (Channel) o;
                if (c.isOpen()) {
                    c.close();
                }
            }
        });
    }

    private <I, O> Flux<O> jsonStreamImpl(io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<I> request, Argument<O> type, Argument<?> errorType, URI requestURI) {
        Flux<HttpResponse<Object>> streamResponsePublisher =
                Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
        return streamResponsePublisher.switchMap(response -> {
            if (!(response instanceof NettyStreamedHttpResponse)) {
                throw new IllegalStateException("Response has been wrapped in non streaming type. Do not wrap the response in client filters for stream requests");
            }

            MapperMediaTypeCodec mediaTypeCodec = (MapperMediaTypeCodec) mediaTypeCodecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE)
                    .orElseThrow(() -> new IllegalStateException("No JSON codec found"));

            StreamedHttpResponse streamResponse = NettyHttpResponseBuilder.toStreamResponse(response);
            Flux<HttpContent> httpContentReactiveSequence = Flux.from(streamResponse);

            boolean isJsonStream = response.getContentType().map(mediaType -> mediaType.equals(MediaType.APPLICATION_JSON_STREAM_TYPE)).orElse(false);
            boolean streamArray = !Iterable.class.isAssignableFrom(type.getType()) && !isJsonStream;
            Processor<byte[], JsonNode> jsonProcessor = mediaTypeCodec.getJsonMapper().createReactiveParser(p -> {
                httpContentReactiveSequence.map(content -> {
                    ByteBuf chunk = content.content();
                    if (log.isTraceEnabled()) {
                        log.trace("HTTP Client Streaming Response Received Chunk (length: {}) for Request: {} {}",
                                chunk.readableBytes(), request.getMethodName(), request.getUri());
                        traceBody("Chunk", chunk);
                    }
                    try {
                        return ByteBufUtil.getBytes(chunk);
                    } finally {
                        chunk.release();
                    }
                }).subscribe(p);
            }, streamArray);
            return Flux.from(jsonProcessor)
                    .map(jsonNode -> mediaTypeCodec.decode(type, jsonNode));
        }).doOnTerminate(() -> {
            final Object o = request.getAttribute(NettyClientHttpRequest.CHANNEL).orElse(null);
            if (o instanceof Channel) {
                final Channel c = (Channel) o;
                if (c.isOpen()) {
                    c.close();
                }
            }
        });
    }

    private <I> Flux<ByteBuffer<?>> dataStreamImpl(io.micronaut.http.HttpRequest<I> request, Argument<?> errorType, io.micronaut.http.HttpRequest<Object> parentRequest, URI requestURI) {
        Flux<HttpResponse<Object>> streamResponsePublisher = Flux.from(buildStreamExchange(parentRequest, request, requestURI, errorType));
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
                })
                .doOnTerminate(() -> {
                    final Object o = request.getAttribute(NettyClientHttpRequest.CHANNEL).orElse(null);
                    if (o instanceof Channel) {
                        final Channel c = (Channel) o;
                        if (c.isOpen()) {
                            c.close();
                        }
                    }
                });
    }

    /**
     * Implementation of {@link #jsonStream}, {@link #dataStream}, {@link #exchangeStream}.
     */
    @SuppressWarnings("MagicNumber")
    private  <I> Publisher<MutableHttpResponse<Object>> buildStreamExchange(
            @Nullable io.micronaut.http.HttpRequest<?> parentRequest,
            @NonNull io.micronaut.http.HttpRequest<I> request,
            @NonNull URI requestURI,
            @Nullable Argument<?> errorType) {

        AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper = new AtomicReference<>(request);
        Flux<MutableHttpResponse<Object>> streamResponsePublisher = connectAndStream(parentRequest, request, requestURI, buildSslContext(requestURI), requestWrapper, false, true);

        streamResponsePublisher = readBodyOnError(errorType, streamResponsePublisher);

        // apply filters
        streamResponsePublisher = Flux.from(
                applyFilterToResponsePublisher(parentRequest, request, requestURI, requestWrapper, streamResponsePublisher)
        );

        return streamResponsePublisher.subscribeOn(scheduler);
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(@NonNull io.micronaut.http.HttpRequest<?> request) {
        return proxy(request, false);
    }

    @Override
    public Publisher<MutableHttpResponse<?>> proxy(@NonNull io.micronaut.http.HttpRequest<?> request, boolean retainHostHeader) {
        return Flux.from(resolveRequestURI(request))
                .flatMap(requestURI -> {
                    io.micronaut.http.MutableHttpRequest<?> httpRequest = request instanceof MutableHttpRequest
                            ? (io.micronaut.http.MutableHttpRequest<?>) request
                            : request.mutate();
                    if (!retainHostHeader) {
                        httpRequest.headers(headers -> headers.remove(HttpHeaderNames.HOST));
                    }

                    AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper = new AtomicReference<>(httpRequest);
                    Flux<MutableHttpResponse<Object>> proxyResponsePublisher = connectAndStream(request, request, requestURI, buildSslContext(requestURI), requestWrapper, true, false);
                    // apply filters
                    //noinspection unchecked
                    proxyResponsePublisher = Flux.from(
                            applyFilterToResponsePublisher(
                                    request,
                                    requestWrapper.get(),
                                    requestURI,
                                    requestWrapper,
                                    (Publisher) proxyResponsePublisher
                            )
                    );
                    return proxyResponsePublisher;
                });
    }

    private <I> Flux<MutableHttpResponse<Object>> connectAndStream(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            SslContext sslContext,
            AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper,
            boolean isProxy,
            boolean failOnError
    ) {
        return Flux.create(emitter -> {
            ChannelFuture channelFuture;
            try {
                if (httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {

                    channelFuture = doConnect(request, requestURI, sslContext, true, isProxy, channelHandlerContext -> {
                        try {
                            final Channel channel = channelHandlerContext.channel();
                            request.setAttribute(NettyClientHttpRequest.CHANNEL, channel);
                            this.streamRequestThroughChannel(
                                    parentRequest,
                                    requestWrapper.get(),
                                    channel,
                                    failOnError
                            ).subscribe(new ForwardingSubscriber<>(emitter));
                        } catch (Exception e) {
                            emitter.error(e);
                        }
                    });
                } else {
                    channelFuture = doConnect(request, requestURI, sslContext, true, isProxy, null);
                    addInstrumentedListener(channelFuture,
                            (ChannelFutureListener) f -> {
                                if (f.isSuccess()) {
                                    Channel channel = f.channel();
                                    request.setAttribute(NettyClientHttpRequest.CHANNEL, channel);
                                    this.streamRequestThroughChannel(
                                            parentRequest,
                                            requestWrapper.get(),
                                            channel,
                                            failOnError
                                    ).subscribe(new ForwardingSubscriber<>(emitter));
                                } else {
                                    Throwable cause = f.cause();
                                    emitter.error(
                                            new HttpClientException("Connect error:" + cause.getMessage(), cause)
                                    );
                                }
                            });
                }
            } catch (HttpClientException e) {
                emitter.error(e);
                return;
            }

            Disposable disposable = buildDisposableChannel(channelFuture);
            emitter.onDispose(disposable);
            emitter.onCancel(disposable);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * Implementation of {@link #exchange(io.micronaut.http.HttpRequest, Argument, Argument)} (after URI resolution).
     */
    private <I, O, E> Publisher<? extends io.micronaut.http.HttpResponse<O>> exchangeImpl(
            URI requestURI,
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            @NonNull Argument<O> bodyType,
            @NonNull Argument<E> errorType) {
        AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper = new AtomicReference<>(request);

        Flux<io.micronaut.http.HttpResponse<O>> responsePublisher = Flux.create(emitter -> {

            boolean multipart = MediaType.MULTIPART_FORM_DATA_TYPE.equals(request.getContentType().orElse(null));
            if (poolMap != null && !multipart) {
                try {
                    RequestKey requestKey = new RequestKey(requestURI);
                    ChannelPool channelPool = poolMap.get(requestKey);
                    Future<Channel> channelFuture = channelPool.acquire();
                    addInstrumentedListener(channelFuture, future -> {
                        if (future.isSuccess()) {
                            Channel channel = future.get();
                            try {
                                sendRequestThroughChannel(
                                        requestWrapper.get(),
                                        bodyType,
                                        errorType,
                                        emitter,
                                        channel,
                                        requestKey.isSecure(),
                                        channelPool
                                );
                            } catch (Exception e) {
                                emitter.error(e);
                            }
                        } else {
                            Throwable cause = future.cause();
                            emitter.error(
                                    new HttpClientException("Connect Error: " + cause.getMessage(), cause)
                            );
                        }
                    });
                } catch (HttpClientException e) {
                    emitter.error(e);
                }
            } else {
                SslContext sslContext = buildSslContext(requestURI);
                ChannelFuture connectionFuture = doConnect(request, requestURI, sslContext, false, null);
                addInstrumentedListener(connectionFuture, future -> {
                    if (!future.isSuccess()) {
                        Throwable cause = future.cause();
                        if (emitter.isCancelled()) {
                            log.trace("Connection to {} failed, but emitter already cancelled.", requestURI, cause);
                        } else {
                            emitter.error(
                                    new HttpClientException("Connect Error: " + cause.getMessage(), cause)
                            );
                        }
                    } else {
                        try {
                            sendRequestThroughChannel(
                                    requestWrapper.get(),
                                    bodyType,
                                    errorType,
                                    emitter,
                                    connectionFuture.channel(),
                                    sslContext != null,
                                    null);
                        } catch (Exception e) {
                            emitter.error(e);
                        }
                    }
                });
            }

        }, FluxSink.OverflowStrategy.ERROR);

        Publisher<io.micronaut.http.HttpResponse<O>> finalPublisher = applyFilterToResponsePublisher(
                parentRequest,
                request,
                requestURI,
                requestWrapper,
                responsePublisher
        );
        Flux<io.micronaut.http.HttpResponse<O>> finalReactiveSequence = Flux.from(finalPublisher);
        // apply timeout to flowable too in case a filter applied another policy
        Optional<Duration> readTimeout = configuration.getReadTimeout();
        if (readTimeout.isPresent()) {
            // add an additional second, because generally the timeout should occur
            // from the Netty request handling pipeline
            final Duration rt = readTimeout.get();
            if (!rt.isNegative()) {
                Duration duration = rt.plus(Duration.ofSeconds(1));
                finalReactiveSequence = finalReactiveSequence.timeout(duration)
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
     * @param channel The channel to close asynchronously
     */
    protected void closeChannelAsync(Channel channel) {
        if (channel.isOpen()) {

            ChannelFuture closeFuture = channel.closeFuture();
            closeFuture.addListener(f2 -> {
                if (!f2.isSuccess() && log.isErrorEnabled()) {
                    Throwable cause = f2.cause();
                    log.error("Error closing request connection: " + cause.getMessage(), cause);
                }
            });
        }
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
     * @param requestURI The request URI
     * @return A URI that is prepended with the contextPath, if set
     */
    protected URI prependContextPath(URI requestURI) {
        if (StringUtils.isNotEmpty(contextPath)) {
            try {
                return new URI(StringUtils.prependUri(contextPath, requestURI.toString()));
            } catch (URISyntaxException e) {
                throw new HttpClientException("Failed to construct the request URI", e);
            }
        }
        return requestURI;
    }

    /**
     * @return The discriminator to use when selecting a server for the purposes of load balancing (defaults to null)
     */
    protected Object getLoadBalancerDiscriminator() {
        return null;
    }

    private void initBootstrapForProxy(Bootstrap bootstrap, boolean ssl, String host, int port) {
        Proxy proxy = configuration.resolveProxy(ssl, host, port);
        if (proxy.type() != Type.DIRECT) {
            bootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
        }
    }

    /**
     * Creates an initial connection to the given remote host.
     *
     * @param request         The request
     * @param uri             The URI to connect to
     * @param sslCtx          The SslContext instance
     * @param isStream        Is the connection a stream connection
     * @param contextConsumer The logic to run once the channel is configured correctly
     * @return A ChannelFuture
     * @throws HttpClientException If the URI is invalid
     */
    protected ChannelFuture doConnect(
            io.micronaut.http.HttpRequest<?> request,
            URI uri,
            @Nullable SslContext sslCtx,
            boolean isStream,
            Consumer<ChannelHandlerContext> contextConsumer) throws HttpClientException {
        return doConnect(request, uri, sslCtx, isStream, false, contextConsumer);
    }

    /**
     * Creates an initial connection to the given remote host.
     *
     * @param request         The request
     * @param uri             The URI to connect to
     * @param sslCtx          The SslContext instance
     * @param isStream        Is the connection a stream connection
     * @param isProxy         Is this a streaming proxy
     * @param contextConsumer The logic to run once the channel is configured correctly
     * @return A ChannelFuture
     * @throws HttpClientException If the URI is invalid
     */
    protected ChannelFuture doConnect(
            io.micronaut.http.HttpRequest<?> request,
            URI uri,
            @Nullable SslContext sslCtx,
            boolean isStream,
            boolean isProxy,
            Consumer<ChannelHandlerContext> contextConsumer) throws HttpClientException {

        RequestKey requestKey = new RequestKey(uri);
        return doConnect(request, requestKey.getHost(), requestKey.getPort(), sslCtx, isStream, isProxy, contextConsumer);
    }

    /**
     * Creates an initial connection to the given remote host.
     *
     * @param request         The request
     * @param host            The host
     * @param port            The port
     * @param sslCtx          The SslContext instance
     * @param isStream        Is the connection a stream connection
     * @param contextConsumer The logic to run once the channel is configured correctly
     * @return A ChannelFuture
     */
    protected ChannelFuture doConnect(
            io.micronaut.http.HttpRequest<?> request,
            String host,
            int port,
            @Nullable SslContext sslCtx,
            boolean isStream,
            Consumer<ChannelHandlerContext> contextConsumer) {
        return doConnect(request, host, port, sslCtx, isStream, false, contextConsumer);
    }

    /**
     * Creates an initial connection to the given remote host.
     *
     * @param request         The request
     * @param host            The host
     * @param port            The port
     * @param sslCtx          The SslContext instance
     * @param isStream        Is the connection a stream connection
     * @param isProxy         Is this a streaming proxy
     * @param contextConsumer The logic to run once the channel is configured correctly
     * @return A ChannelFuture
     */
    protected ChannelFuture doConnect(
            io.micronaut.http.HttpRequest<?> request,
            String host,
            int port,
            @Nullable SslContext sslCtx,
            boolean isStream,
            boolean isProxy,
            Consumer<ChannelHandlerContext> contextConsumer) {
        Bootstrap localBootstrap = this.bootstrap.clone();
        initBootstrapForProxy(localBootstrap, sslCtx != null, host, port);
        String acceptHeader = request.getHeaders().get(io.micronaut.http.HttpHeaders.ACCEPT);
        localBootstrap.handler(new HttpClientInitializer(
                sslCtx,
                host,
                port,
                isStream,
                isProxy,
                acceptHeader != null && acceptHeader.equalsIgnoreCase(MediaType.TEXT_EVENT_STREAM), contextConsumer)
        );
        return doConnect(localBootstrap, host, port);
    }

    /**
     * Creates the {@link NioEventLoopGroup} for this client.
     *
     * @param configuration The configuration
     * @param threadFactory The thread factory
     * @return The group
     */
    protected NioEventLoopGroup createEventLoopGroup(HttpClientConfiguration configuration, ThreadFactory threadFactory) {
        OptionalInt numOfThreads = configuration.getNumOfThreads();
        Optional<Class<? extends ThreadFactory>> threadFactoryType = configuration.getThreadFactory();
        boolean hasThreads = numOfThreads.isPresent();
        boolean hasFactory = threadFactoryType.isPresent();
        NioEventLoopGroup group;
        if (hasThreads && hasFactory) {
            group = new NioEventLoopGroup(numOfThreads.getAsInt(), InstantiationUtils.instantiate(threadFactoryType.get()));
        } else if (hasThreads) {
            if (threadFactory != null) {
                group = new NioEventLoopGroup(numOfThreads.getAsInt(), threadFactory);
            } else {
                group = new NioEventLoopGroup(numOfThreads.getAsInt());
            }
        } else {
            if (threadFactory != null) {
                group = new NioEventLoopGroup(NettyThreadFactory.DEFAULT_EVENT_LOOP_THREADS, threadFactory);
            } else {

                group = new NioEventLoopGroup();
            }
        }
        return group;
    }

    /**
     * Creates an initial connection with the given bootstrap and remote host.
     *
     * @param bootstrap The bootstrap instance
     * @param host      The host
     * @param port      The port
     * @return The ChannelFuture
     */
    protected ChannelFuture doConnect(Bootstrap bootstrap, String host, int port) {
        return bootstrap.connect(host, port);
    }

    /**
     * Builds an {@link SslContext} for the given URI if necessary.
     *
     * @param uriObject The URI
     * @return The {@link SslContext} instance
     */
    protected SslContext buildSslContext(URI uriObject) {
        final SslContext sslCtx;
        if (isSecureScheme(uriObject.getScheme())) {
            sslCtx = sslContext;
            //Allow https requests to be sent if SSL is disabled but a proxy is present
            if (sslCtx == null && !configuration.getProxyAddress().isPresent()) {
                throw new HttpClientException("Cannot send HTTPS request. SSL is disabled");
            }
        } else {
            sslCtx = null;
        }
        return sslCtx;
    }

    /**
     * Configures the HTTP proxy for the pipeline.
     *
     * @param pipeline The pipeline
     * @param proxy    The proxy
     */
    protected void configureProxy(ChannelPipeline pipeline, Proxy proxy) {
        configureProxy(pipeline, proxy.type(), proxy.address());
    }

    /**
     * Configures the HTTP proxy for the pipeline.
     *
     * @param pipeline     The pipeline
     * @param proxyType    The proxy type
     * @param proxyAddress The proxy address
     */
    protected void configureProxy(ChannelPipeline pipeline, Type proxyType, SocketAddress proxyAddress) {
        String username = configuration.getProxyUsername().orElse(null);
        String password = configuration.getProxyPassword().orElse(null);

        if (proxyAddress instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) proxyAddress;
            if (isa.isUnresolved()) {
                proxyAddress = new InetSocketAddress(isa.getHostString(), isa.getPort());
            }
        }

        if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
            switch (proxyType) {
                case HTTP:
                    pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_PROXY, new HttpProxyHandler(proxyAddress, username, password));
                    break;
                case SOCKS:
                    pipeline.addLast(ChannelPipelineCustomizer.HANDLER_SOCKS_5_PROXY, new Socks5ProxyHandler(proxyAddress, username, password));
                    break;
                default:
                    // no-op
            }
        } else {
            switch (proxyType) {
                case HTTP:
                    pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_PROXY, new HttpProxyHandler(proxyAddress));
                    break;
                case SOCKS:
                    pipeline.addLast(ChannelPipelineCustomizer.HANDLER_SOCKS_5_PROXY, new Socks5ProxyHandler(proxyAddress));
                    break;
                default:
                    // no-op
            }
        }
    }

    private <I, O, R extends io.micronaut.http.HttpResponse<O>> Publisher<R> applyFilterToResponsePublisher(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper,
            Publisher<R> responsePublisher) {

        if (request instanceof MutableHttpRequest) {
            ((MutableHttpRequest<I>) request).uri(requestURI);

            List<HttpClientFilter> filters =
                    filterResolver.resolveFilters(request, clientFilterEntries);
            if (parentRequest != null) {
                filters.add(new ClientServerContextFilter(parentRequest));
            }

            OrderUtil.reverseSort(filters);
            Publisher<R> finalResponsePublisher = responsePublisher;
            filters.add((req, chain) -> finalResponsePublisher);

            ClientFilterChain filterChain = buildChain(requestWrapper, filters);
            if (parentRequest != null) {
                responsePublisher = ServerRequestContext.with(parentRequest, (Supplier<Publisher<R>>) () -> {
                    try {
                        return Flux.from((Publisher<R>) filters.get(0).doFilter(request, filterChain))
                                .contextWrite(ctx -> ctx.put(ServerRequestContext.KEY, parentRequest));
                    } catch (Throwable t) {
                        return Flux.error(t);
                    }
                });
            } else {
                try {
                    responsePublisher = (Publisher<R>) filters.get(0).doFilter(request, filterChain);
                } catch (Throwable t) {
                    responsePublisher = Flux.error(t);
                }
            }
        }

        return responsePublisher;
    }

    /**
     * @param request                The request
     * @param requestURI             The URI of the request
     * @param requestContentType     The request content type
     * @param permitsBody            Whether permits body
     * @param bodyType               The body type
     * @param onError                Called when the body publisher encounters an error
     * @param closeChannelAfterWrite Whether to close the channel. For stream requests we don't close the channel until disposed of.
     * @return A {@link NettyRequestWriter}
     * @throws HttpPostRequestEncoder.ErrorDataEncoderException if there is an encoder exception
     */
    protected NettyRequestWriter buildNettyRequest(
            MutableHttpRequest request,
            URI requestURI,
            MediaType requestContentType,
            boolean permitsBody,
            @Nullable Argument<?> bodyType,
            Consumer<? super Throwable> onError,
            boolean closeChannelAfterWrite) throws HttpPostRequestEncoder.ErrorDataEncoderException {

        io.netty.handler.codec.http.HttpRequest nettyRequest;
        HttpPostRequestEncoder postRequestEncoder = null;
        if (permitsBody) {
            Optional body = request.getBody();
            boolean hasBody = body.isPresent();
            if (requestContentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE) && hasBody) {
                Object bodyValue = body.get();
                if (bodyValue instanceof CharSequence) {
                    ByteBuf byteBuf = charSequenceToByteBuf((CharSequence) bodyValue, requestContentType);
                    request.body(byteBuf);
                    nettyRequest = NettyHttpRequestBuilder.toHttpRequest(request);
                } else {
                    postRequestEncoder = buildFormDataRequest(request, bodyValue);
                    nettyRequest = postRequestEncoder.finalizeRequest();
                }
            } else if (requestContentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE) && hasBody) {
                Object bodyValue = body.get();
                postRequestEncoder = buildMultipartRequest(request, bodyValue);
                nettyRequest = postRequestEncoder.finalizeRequest();
            } else {
                ByteBuf bodyContent = null;
                if (hasBody) {
                    Object bodyValue = body.get();
                    if (Publishers.isConvertibleToPublisher(bodyValue)) {
                        boolean isSingle = Publishers.isSingle(bodyValue.getClass());

                        Publisher<?> publisher = ConversionService.SHARED.convert(bodyValue, Publisher.class).orElseThrow(() ->
                                new IllegalArgumentException("Unconvertible reactive type: " + bodyValue)
                        );

                        Flux<HttpContent> requestBodyPublisher = Flux.from(publisher).map(o -> {
                            if (o instanceof CharSequence) {
                                ByteBuf textChunk = Unpooled.copiedBuffer(((CharSequence) o), requestContentType.getCharset().orElse(StandardCharsets.UTF_8));
                                if (log.isTraceEnabled()) {
                                    traceChunk(textChunk);
                                }
                                return new DefaultHttpContent(textChunk);
                            } else if (o instanceof ByteBuf) {
                                ByteBuf byteBuf = (ByteBuf) o;
                                if (log.isTraceEnabled()) {
                                    log.trace("Sending Bytes Chunk. Length: {}", byteBuf.readableBytes());
                                }
                                return new DefaultHttpContent(byteBuf);
                            } else if (o instanceof byte[]) {
                                byte[] bodyBytes = (byte[]) o;
                                if (log.isTraceEnabled()) {
                                    log.trace("Sending Bytes Chunk. Length: {}", bodyBytes.length);
                                }
                                return new DefaultHttpContent(Unpooled.wrappedBuffer(bodyBytes));
                            } else if (o instanceof ByteBuffer) {
                                ByteBuffer<?> byteBuffer = (ByteBuffer<?>) o;
                                Object nativeBuffer = byteBuffer.asNativeBuffer();
                                if (log.isTraceEnabled()) {
                                    log.trace("Sending Bytes Chunk. Length: {}", byteBuffer.readableBytes());
                                }
                                if (nativeBuffer instanceof ByteBuf) {
                                    return new DefaultHttpContent((ByteBuf) nativeBuffer);
                                } else {
                                    return new DefaultHttpContent(Unpooled.wrappedBuffer(byteBuffer.toByteArray()));
                                }
                            } else if (mediaTypeCodecRegistry != null) {
                                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(requestContentType);
                                ByteBuf encoded = registeredCodec.map(codec -> {
                                            if (bodyType != null && bodyType.isInstance(o)) {
                                                return codec.encode((Argument<Object>) bodyType, o, byteBufferFactory).asNativeBuffer();
                                            } else {
                                                return codec.encode(o, byteBufferFactory).asNativeBuffer();
                                            }
                                        })
                                        .orElse(null);
                                if (encoded != null) {
                                    if (log.isTraceEnabled()) {
                                        traceChunk(encoded);
                                    }
                                    return new DefaultHttpContent(encoded);
                                }
                            }
                            throw new CodecException("Cannot encode value [" + o + "]. No possible encoders found");
                        });

                        if (!isSingle && MediaType.APPLICATION_JSON_TYPE.equals(requestContentType)) {
                           requestBodyPublisher = JsonSubscriber.lift(requestBodyPublisher);
                        }

                        requestBodyPublisher = requestBodyPublisher.doOnError(onError);

                        request.body(requestBodyPublisher);
                        nettyRequest = NettyHttpRequestBuilder.toHttpRequest(request);
                        try {
                            nettyRequest.setUri(requestURI.toURL().getFile());
                        } catch (MalformedURLException e) {
                            //should never happen
                        }
                        return new NettyRequestWriter(requestURI.getScheme(), nettyRequest, null, closeChannelAfterWrite);
                    } else if (bodyValue instanceof CharSequence) {
                        bodyContent = charSequenceToByteBuf((CharSequence) bodyValue, requestContentType);
                    } else if (mediaTypeCodecRegistry != null) {
                        Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(requestContentType);
                        bodyContent = registeredCodec.map(codec -> {
                                    if (bodyType != null && bodyType.isInstance(bodyValue)) {
                                        return codec.encode((Argument<Object>) bodyType, bodyValue, byteBufferFactory).asNativeBuffer();
                                    } else {
                                        return codec.encode(bodyValue, byteBufferFactory).asNativeBuffer();
                                    }
                                })
                                .orElse(null);
                    }
                    if (bodyContent == null) {
                        bodyContent = ConversionService.SHARED.convert(bodyValue, ByteBuf.class).orElseThrow(() ->
                                new HttpClientException("Body [" + bodyValue + "] cannot be encoded to content type [" + requestContentType + "]. No possible codecs or converters found.")
                        );
                    }
                }
                request.body(bodyContent);
                try {
                    nettyRequest = NettyHttpRequestBuilder.toHttpRequest(request);
                } finally {
                    // reset body after encoding request in case of retry
                    request.body(body.orElse(null));
                }
            }
        } else {
            nettyRequest = NettyHttpRequestBuilder.toHttpRequest(request);
        }
        try {
            nettyRequest.setUri(requestURI.toURL().getFile());
        } catch (MalformedURLException e) {
            //should never happen
        }
        return new NettyRequestWriter(requestURI.getScheme(), nettyRequest, postRequestEncoder, closeChannelAfterWrite);
    }

    /**
     * Configures HTTP/2 for the channel when SSL is enabled.
     *
     * @param httpClientInitializer The client initializer
     * @param ch                    The channel
     * @param sslCtx                The SSL context
     * @param host                  The host
     * @param port                  The port
     * @param connectionHandler     The connection handler
     */
    protected void configureHttp2Ssl(
            HttpClientInitializer httpClientInitializer,
            @NonNull SocketChannel ch,
            @NonNull SslContext sslCtx,
            String host,
            int port,
            HttpToHttp2ConnectionHandler connectionHandler) {
        ChannelPipeline pipeline = ch.pipeline();
        // Specify Host in SSLContext New Handler to add TLS SNI Extension
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_SSL, sslCtx.newHandler(ch.alloc(), host, port));
        // We must wait for the handshake to finish and the protocol to be negotiated before configuring
        // the HTTP/2 components of the pipeline.
        pipeline.addLast(
                ChannelPipelineCustomizer.HANDLER_HTTP2_PROTOCOL_NEGOTIATOR,
                new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_2) {

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) {
                // the logic to send the request should only be executed once the HTTP/2
                // Connection Preface request has been sent. Once the Preface has been sent and
                // removed then this handler is removed so we invoke the remaining logic once
                // this handler removed
                final Consumer<ChannelHandlerContext> contextConsumer =
                        httpClientInitializer.contextConsumer;
                if (contextConsumer != null) {
                    contextConsumer.accept(ctx);
                }
            }

            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                    ChannelPipeline p = ctx.pipeline();
                    if (httpClientInitializer.stream) {
                        // stream consumer manages backpressure and reads
                        ctx.channel().config().setAutoRead(false);
                    }
                    p.addLast(
                            ChannelPipelineCustomizer.HANDLER_HTTP2_SETTINGS,
                            new Http2SettingsHandler(ch.newPromise())
                    );
                    httpClientInitializer.addEventStreamHandlerIfNecessary(p);
                    httpClientInitializer.addFinalHandler(p);
                    for (ChannelPipelineListener pipelineListener : pipelineListeners) {
                        pipelineListener.onConnect(p);
                    }
                } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                    ChannelPipeline p = ctx.pipeline();
                    httpClientInitializer.addHttp1Handlers(p);
                } else {
                    ctx.close();
                    throw new HttpClientException("Unknown Protocol: " + protocol);
                }
            }
        });

        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, connectionHandler);
    }

    /**
     * Configures HTTP/2 handling for plaintext (non-SSL) connections.
     *
     * @param httpClientInitializer The client initializer
     * @param ch                    The channel
     * @param connectionHandler     The connection handler
     */
    protected void configureHttp2ClearText(
            HttpClientInitializer httpClientInitializer,
            @NonNull SocketChannel ch,
            @NonNull HttpToHttp2ConnectionHandler connectionHandler) {
        HttpClientCodec sourceCodec = new HttpClientCodec();
        Http2ClientUpgradeCodec upgradeCodec = new Http2ClientUpgradeCodec(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, connectionHandler);
        HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536);

        final ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC, sourceCodec);
        httpClientInitializer.settingsHandler = new Http2SettingsHandler(ch.newPromise());
        pipeline.addLast(upgradeHandler);
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_UPGRADE_REQUEST, new UpgradeRequestHandler(httpClientInitializer) {
            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) {
                final Consumer<ChannelHandlerContext> contextConsumer = httpClientInitializer.contextConsumer;
                if (contextConsumer != null) {
                    contextConsumer.accept(ctx);
                }
            }
        });
    }

    /**
     * Creates a new {@link HttpToHttp2ConnectionHandlerBuilder} for the given HTTP/2 connection object and config.
     *
     * @param connection    The connection
     * @param configuration The configuration
     * @param stream        Whether this is a stream request
     * @return The {@link HttpToHttp2ConnectionHandlerBuilder}
     */
    protected @NonNull
    HttpToHttp2ConnectionHandlerBuilder newHttp2ConnectionHandlerBuilder(
            @NonNull Http2Connection connection, @NonNull HttpClientConfiguration configuration, boolean stream) {
        final HttpToHttp2ConnectionHandlerBuilder builder = new HttpToHttp2ConnectionHandlerBuilder();
        builder.validateHeaders(true);
        final Http2FrameListener http2ToHttpAdapter;

        if (!stream) {
            http2ToHttpAdapter = new InboundHttp2ToHttpAdapterBuilder(connection)
                    .maxContentLength(configuration.getMaxContentLength())
                    .validateHttpHeaders(true)
                    .propagateSettings(true)
                    .build();

        } else {
            http2ToHttpAdapter = new StreamingInboundHttp2ToHttpAdapter(
                    connection,
                    configuration.getMaxContentLength()
            );
        }
        return builder
                .connection(connection)
                .frameListener(new DelegatingDecompressorFrameListener(
                        connection,
                        http2ToHttpAdapter));

    }

    private Flux<MutableHttpResponse<Object>> readBodyOnError(@Nullable Argument<?> errorType, @NonNull Flux<MutableHttpResponse<Object>> publisher) {
        if (errorType != null && errorType != HttpClient.DEFAULT_ERROR_TYPE) {
            return publisher.onErrorResume(clientException -> {
                if (clientException instanceof HttpClientResponseException) {
                    final HttpResponse<?> response = ((HttpClientResponseException) clientException).getResponse();
                    if (response instanceof NettyStreamedHttpResponse) {
                        return Mono.create(emitter -> {
                            NettyStreamedHttpResponse<?> streamedResponse = (NettyStreamedHttpResponse<?>) response;
                            final StreamedHttpResponse nettyResponse = streamedResponse.getNettyResponse();
                            nettyResponse.subscribe(new Subscriber<HttpContent>() {
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
                                        final FullNettyClientHttpResponse<Object> fullNettyClientHttpResponse = new FullNettyClientHttpResponse<>(fullHttpResponse, response.status(), mediaTypeCodecRegistry, byteBufferFactory, (Argument<Object>) errorType, true);
                                        fullNettyClientHttpResponse.onComplete();
                                        emitter.error(new HttpClientResponseException(
                                                fullHttpResponse.status().reasonPhrase(),
                                                null,
                                                fullNettyClientHttpResponse,
                                                new HttpClientErrorDecoder() {
                                                    @Override
                                                    public Argument<?> getErrorType(MediaType mediaType) {
                                                        return errorType;
                                                    }
                                                }
                                        ));
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
            return Flux.error(new NoHostException("Request URI specifies no host to connect to"));
        }

        return Flux.from(loadBalancer.select(getLoadBalancerDiscriminator())).map(server -> {
                    Optional<String> authInfo = server.getMetadata().get(io.micronaut.http.HttpHeaders.AUTHORIZATION_INFO, String.class);
                    if (request instanceof MutableHttpRequest && authInfo.isPresent()) {
                        ((MutableHttpRequest) request).getHeaders().auth(authInfo.get());
                    }
                    return server.resolve(includeContextPath ? prependContextPath(requestURI) : requestURI);
                }
        );
    }

    private <I, O, E> void sendRequestThroughChannel(
            io.micronaut.http.HttpRequest<I> finalRequest,
            Argument<O> bodyType,
            Argument<E> errorType,
            FluxSink<? super HttpResponse<O>> emitter,
            Channel channel,
            boolean secure,
            ChannelPool channelPool) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        URI requestURI = finalRequest.getUri();
        MediaType requestContentType = finalRequest
                .getContentType()
                .orElse(MediaType.APPLICATION_JSON_TYPE);

        boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(finalRequest.getMethod());

        MutableHttpRequest clientHttpRequest = (MutableHttpRequest) finalRequest;
        NettyRequestWriter requestWriter = buildNettyRequest(
                clientHttpRequest,
                requestURI,
                requestContentType,
                permitsBody,
                bodyType,
                throwable -> {
                    if (!emitter.isCancelled()) {
                        emitter.error(throwable);
                    }
                },
                true
        );
        HttpRequest nettyRequest = requestWriter.getNettyRequest();

        prepareHttpHeaders(
                requestURI,
                finalRequest,
                nettyRequest,
                permitsBody,
                poolMap == null
        );

        if (log.isDebugEnabled()) {
            debugRequest(requestURI, nettyRequest);
        }

        if (log.isTraceEnabled()) {
            traceRequest(finalRequest, nettyRequest);
        }

        Promise<HttpResponse<O>> responsePromise = channel.eventLoop().newPromise();
        channel.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_FULL_HTTP_RESPONSE,
                new FullHttpResponseHandler<>(responsePromise, channelPool, secure, finalRequest, bodyType, errorType));
        Publisher<HttpResponse<O>> publisher = new NettyFuturePublisher<>(responsePromise, true);
        if (bodyType != null && bodyType.isVoid()) {
            // don't emit response if bodyType is void
            publisher = Flux.from(publisher).filter(r -> false);
        }
        publisher.subscribe(new ForwardingSubscriber<>(emitter));

        requestWriter.writeAndClose(channel, channelPool, emitter);
    }

    private Flux<MutableHttpResponse<Object>> streamRequestThroughChannel(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<?> request,
            Channel channel,
            boolean failOnError) {
        return Flux.<MutableHttpResponse<Object>>create(sink -> {
            try {
                streamRequestThroughChannel0(parentRequest, request, sink, channel);
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
            return Flux.error(new HttpClientResponseException(response.getStatus().getReason(), response));
        } else {
            return Flux.just(response);
        }
    }

    private void streamRequestThroughChannel0(
            io.micronaut.http.HttpRequest<?> parentRequest,
            final io.micronaut.http.HttpRequest<?> finalRequest,
            FluxSink emitter,
            Channel channel) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        NettyRequestWriter requestWriter = prepareRequest(
                finalRequest,
                finalRequest.getUri(),
                emitter,
                false
        );
        HttpRequest nettyRequest = requestWriter.getNettyRequest();
        Promise<HttpResponse<?>> responsePromise = channel.eventLoop().newPromise();
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE_FULL, new StreamFullHttpResponseHandler(responsePromise, parentRequest, finalRequest));
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE_STREAM, new StreamStreamHttpResponseHandler(responsePromise, parentRequest, finalRequest));

        if (log.isDebugEnabled()) {
            debugRequest(finalRequest.getUri(), nettyRequest);
        }

        if (log.isTraceEnabled()) {
            traceRequest(finalRequest, nettyRequest);
        }

        requestWriter.writeAndClose(channel, null, emitter);
        responsePromise.addListener(future -> {
            if (future.isSuccess()) {
                emitter.next(future.getNow());
                emitter.complete();
            } else {
                emitter.error(future.cause());
            }
        });
    }

    private ByteBuf charSequenceToByteBuf(CharSequence bodyValue, MediaType requestContentType) {
        CharSequence charSequence = bodyValue;
        return byteBufferFactory.copiedBuffer(
                charSequence.toString().getBytes(
                        requestContentType.getCharset().orElse(defaultCharset)
                )
        ).asNativeBuffer();
    }

    private String getHostHeader(URI requestURI) {
        RequestKey requestKey = new RequestKey(requestURI);
        StringBuilder host = new StringBuilder(requestKey.getHost());
        int port = requestKey.getPort();
        if (port > -1 && port != 80 && port != 443) {
            host.append(":").append(port);
        }
        return host.toString();
    }

    private <I> void prepareHttpHeaders(
            URI requestURI,
            io.micronaut.http.HttpRequest<I> request,
            io.netty.handler.codec.http.HttpRequest nettyRequest,
            boolean permitsBody,
            boolean closeConnection) {
        HttpHeaders headers = nettyRequest.headers();

        if (!headers.contains(HttpHeaderNames.HOST)) {
            headers.set(HttpHeaderNames.HOST, getHostHeader(requestURI));
        }

        // HTTP/2 assumes keep-alive connections
        if (httpVersion != io.micronaut.http.HttpVersion.HTTP_2_0) {
            if (closeConnection) {
                headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            } else {
                headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
        }

        if (permitsBody) {
            Optional<I> body = request.getBody();
            if (body.isPresent()) {
                if (!headers.contains(HttpHeaderNames.CONTENT_TYPE)) {
                    MediaType mediaType = request.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
                    headers.set(HttpHeaderNames.CONTENT_TYPE, mediaType);
                }
                if (nettyRequest instanceof FullHttpRequest) {
                    FullHttpRequest fullHttpRequest = (FullHttpRequest) nettyRequest;
                    headers.set(HttpHeaderNames.CONTENT_LENGTH, fullHttpRequest.content().readableBytes());
                } else {
                    if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !headers.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                        headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                    }
                }
            } else if (!(nettyRequest instanceof StreamedHttpRequest)) {
                headers.set(HttpHeaderNames.CONTENT_LENGTH, 0);
            }
        }
    }

    /**
     * Note: caller must ensure this is only called for plaintext HTTP, not TLS HTTP2.
     */
    private boolean discardH2cStream(HttpMessage message) {
        // only applies to h2c
        if (httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {
            int streamId = message.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), -1);
            if (streamId == 1) {
                // ignore this message
                if (log.isDebugEnabled()) {
                    log.debug("Received response on HTTP2 stream 1, the stream used to respond to the initial upgrade request. Ignoring.");
                }
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void addReadTimeoutHandler(ChannelPipeline pipeline) {
        if (readTimeoutMillis != null) {
            if (httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {
                Http2SettingsHandler settingsHandler = (Http2SettingsHandler) pipeline.get(HANDLER_HTTP2_SETTINGS);
                if (settingsHandler != null) {
                    addInstrumentedListener(settingsHandler.promise, future -> {
                        if (future.isSuccess()) {
                            pipeline.addBefore(
                                    ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION,
                                    ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT,
                                    new ReadTimeoutHandler(readTimeoutMillis, TimeUnit.MILLISECONDS)
                            );
                        }

                    });
                } else {
                    pipeline.addBefore(
                            ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION,
                            ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT,
                            new ReadTimeoutHandler(readTimeoutMillis, TimeUnit.MILLISECONDS)
                    );
                }
            } else {
                pipeline.addBefore(
                        ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC,
                        ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT,
                        new ReadTimeoutHandler(readTimeoutMillis, TimeUnit.MILLISECONDS));
            }
        }
    }

    private void removeReadTimeoutHandler(ChannelPipeline pipeline) {
        if (readTimeoutMillis != null && pipeline.context(ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT) != null) {
            pipeline.remove(ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT);
        }
    }

    private ClientFilterChain buildChain(AtomicReference<io.micronaut.http.HttpRequest<?>> requestWrapper, List<HttpClientFilter> filters) {
        AtomicInteger integer = new AtomicInteger();
        int len = filters.size();
        return new ClientFilterChain() {
            @Override
            public Publisher<? extends io.micronaut.http.HttpResponse<?>> proceed(MutableHttpRequest<?> request) {

                int pos = integer.incrementAndGet();
                if (pos > len) {
                    throw new IllegalStateException("The FilterChain.proceed(..) method should be invoked exactly once per filter execution. The method has instead been invoked multiple times by an erroneous filter definition.");
                }
                HttpClientFilter httpFilter = filters.get(pos);
                try {
                    return httpFilter.doFilter(requestWrapper.getAndSet(request), this);
                } catch (Throwable t) {
                    return Flux.error(t);
                }
            }
        };
    }

    private HttpPostRequestEncoder buildFormDataRequest(MutableHttpRequest clientHttpRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(NettyHttpRequestBuilder.toHttpRequest(clientHttpRequest), false);

        Map<String, Object> formData;
        if (bodyValue instanceof Map) {
            formData = (Map<String, Object>) bodyValue;
        } else {
            formData = BeanMap.of(bodyValue);
        }
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                if (value instanceof Collection) {
                    Collection collection = (Collection) value;
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
        Optional<String> converted = ConversionService.SHARED.convert(value, String.class);
        if (converted.isPresent()) {
            postRequestEncoder.addBodyAttribute(key, converted.get());
        }
    }

    private HttpPostRequestEncoder buildMultipartRequest(MutableHttpRequest clientHttpRequest, Object bodyValue) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);
        io.netty.handler.codec.http.HttpRequest request = NettyHttpRequestBuilder.toHttpRequest(clientHttpRequest);
        HttpPostRequestEncoder postRequestEncoder = new HttpPostRequestEncoder(factory, request, true, CharsetUtil.UTF_8, HttpPostRequestEncoder.EncoderMode.HTML5);
        if (bodyValue instanceof MultipartBody.Builder) {
            bodyValue = ((MultipartBody.Builder) bodyValue).build();
        }
        if (bodyValue instanceof MultipartBody) {
            final MultipartBody multipartBody = (MultipartBody) bodyValue;
            postRequestEncoder.setBodyHttpDatas(multipartBody.getData(new MultipartDataFactory<InterfaceHttpData>() {
                @NonNull
                @Override
                public InterfaceHttpData createFileUpload(@NonNull String name, @NonNull String filename, @NonNull MediaType contentType, @Nullable String encoding, @Nullable Charset charset, long length) {
                    return factory.createFileUpload(
                            request,
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
                            request,
                            name,
                            value
                    );
                }

                @Override
                public void setContent(InterfaceHttpData fileUploadObject, Object content) throws IOException {
                    if (fileUploadObject instanceof FileUpload) {
                        FileUpload fu = (FileUpload) fileUploadObject;
                        if (content instanceof InputStream) {
                            fu.setContent((InputStream) content);
                        } else if (content instanceof File) {
                            fu.setContent((File) content);
                        } else if (content instanceof byte[]) {
                            final ByteBuf buffer = Unpooled.wrappedBuffer((byte[]) content);
                            fu.setContent(buffer);
                        }
                    }
                }
            }));
        } else {
            throw new MultipartException(String.format("The type %s is not a supported type for a multipart request body", bodyValue.getClass().getName()));
        }

        return postRequestEncoder;
    }

    private void debugRequest(URI requestURI, io.netty.handler.codec.http.HttpRequest nettyRequest) {
        log.debug("Sending HTTP {} to {}",
                nettyRequest.method(),
                requestURI.toString());
    }

    private void traceRequest(io.micronaut.http.HttpRequest<?> request, io.netty.handler.codec.http.HttpRequest nettyRequest) {
        HttpHeaders headers = nettyRequest.headers();
        traceHeaders(headers);
        if (io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod()) && request.getBody().isPresent() && nettyRequest instanceof FullHttpRequest) {
            FullHttpRequest fullHttpRequest = (FullHttpRequest) nettyRequest;
            ByteBuf content = fullHttpRequest.content();
            if (log.isTraceEnabled()) {
                traceBody("Request", content);
            }
        }
    }

    private void traceBody(String type, ByteBuf content) {
        log.trace(type + " Body");
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

    private void traceHeaders(HttpHeaders headers) {
        for (String name : headers.names()) {
            List<String> all = headers.getAll(name);
            if (all.size() > 1) {
                for (String value : all) {
                    log.trace("{}: {}", name, value);
                }
            } else if (!all.isEmpty()) {
                log.trace("{}: {}", name, all.get(0));
            }
        }
    }

    private static MediaTypeCodecRegistry createDefaultMediaTypeRegistry() {
        JsonMapper mapper = new JacksonDatabindMapper();
        ApplicationConfiguration configuration = new ApplicationConfiguration();
        return MediaTypeCodecRegistry.of(
                new JsonMediaTypeCodec(mapper, configuration, null),
                new JsonStreamMediaTypeCodec(mapper, configuration, null)
        );
    }

    private <I> NettyRequestWriter prepareRequest(
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            FluxSink<HttpResponse<Object>> emitter,
            boolean closeChannelAfterWrite) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        MediaType requestContentType = request
                .getContentType()
                .orElse(MediaType.APPLICATION_JSON_TYPE);

        boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod());

        if (!(request instanceof MutableHttpRequest)) {
            throw new IllegalArgumentException("A MutableHttpRequest is required");
        }
        MutableHttpRequest clientHttpRequest = (MutableHttpRequest) request;
        NettyRequestWriter requestWriter = buildNettyRequest(
                clientHttpRequest,
                requestURI,
                requestContentType,
                permitsBody,
                null,
                throwable -> {
                    if (!emitter.isCancelled()) {
                        emitter.error(throwable);
                    }
                },
                closeChannelAfterWrite
        );
        io.netty.handler.codec.http.HttpRequest nettyRequest = requestWriter.getNettyRequest();
        prepareHttpHeaders(requestURI, request, nettyRequest, permitsBody, true);
        return requestWriter;
    }

    private Disposable buildDisposableChannel(ChannelFuture channelFuture) {
        return new Disposable() {
            private AtomicBoolean disposed = new AtomicBoolean(false);

            @Override
            public void dispose() {
                if (disposed.compareAndSet(false, true)) {
                    Channel channel = channelFuture.channel();
                    if (channel.isOpen()) {
                        closeChannelAsync(channel);
                    }
                }
            }

            @Override
            public boolean isDisposed() {
                return disposed.get();
            }
        };
    }

    private AbstractChannelPoolHandler newPoolHandler(RequestKey key) {
        return new AbstractChannelPoolHandler() {
            @Override
            public void channelCreated(Channel ch) {
                ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_INIT, new HttpClientInitializer(
                        key.isSecure() ? sslContext : null,
                        key.getHost(),
                        key.getPort(),
                        false,
                        false,
                        false,
                        null
                ) {
                    @Override
                    protected void addFinalHandler(ChannelPipeline pipeline) {
                        // no-op, don't add the stream handler which is not supported
                        // in the connection pooled scenario
                    }
                });

                if (connectionTimeAliveMillis != null) {
                    ch.pipeline()
                            .addLast(
                                    ChannelPipelineCustomizer.HANDLER_CONNECT_TTL,
                                    new ConnectTTLHandler(connectionTimeAliveMillis)
                            );
                }
            }

            @Override
            public void channelReleased(Channel ch) {
                Duration idleTimeout = configuration.getConnectionPoolIdleTimeout().orElse(Duration.ofNanos(0));
                ChannelPipeline pipeline = ch.pipeline();
                if (ch.isOpen()) {
                    ch.config().setAutoRead(true);
                    pipeline.addLast(IdlingConnectionHandler.INSTANCE);
                    if (idleTimeout.toNanos() > 0) {
                        pipeline.addLast(HANDLER_IDLE_STATE, new IdleStateHandler(idleTimeout.toNanos(), idleTimeout.toNanos(), 0, TimeUnit.NANOSECONDS));
                        pipeline.addLast(IdleTimeoutHandler.INSTANCE);
                    }
                }

                if (connectionTimeAliveMillis != null) {
                    boolean shouldCloseOnRelease = Boolean.TRUE.equals(ch.attr(ConnectTTLHandler.RELEASE_CHANNEL).get());

                    if (shouldCloseOnRelease && ch.isOpen() && !ch.eventLoop().isShuttingDown()) {
                        ch.close();
                    }
                }

                removeReadTimeoutHandler(pipeline);
            }

            @Override
            public void channelAcquired(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                if (pipeline.context(IdlingConnectionHandler.INSTANCE) != null) {
                    pipeline.remove(IdlingConnectionHandler.INSTANCE);
                }
                if (pipeline.context(HANDLER_IDLE_STATE) != null) {
                    pipeline.remove(HANDLER_IDLE_STATE);
                }
                if (pipeline.context(IdleTimeoutHandler.INSTANCE) != null) {
                    pipeline.remove(IdleTimeoutHandler.INSTANCE);
                }
            }
        };
    }

    /**
     * Adds a Netty listener that is instrumented by instrumenters given by managed or provided collection of
     * the {@link InvocationInstrumenterFactory}.
     *
     * @param channelFuture The channel future
     * @param listener The listener logic
     * @param <V> the type of value returned by the future
     * @param <C> the future type
     * @return a Netty listener that is instrumented
     */
    private <V, C extends Future<V>> Future<V> addInstrumentedListener(
            Future<V> channelFuture, GenericFutureListener<C> listener
    ) {
        InvocationInstrumenter instrumenter = combineFactories();

        return channelFuture.addListener(f -> {
            try (Instrumentation ignored = instrumenter.newInstrumentation()) {
                listener.operationComplete((C) f);
            }
        });
    }

    private @NonNull InvocationInstrumenter combineFactories() {
        if (CollectionUtils.isEmpty(invocationInstrumenterFactories)) {
            return NOOP;
        }
        return InvocationInstrumenter.combine(invocationInstrumenterFactories.stream()
                .map(InvocationInstrumenterFactory::newInvocationInstrumenter)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    private static boolean isSecureScheme(String scheme) {
        return io.micronaut.http.HttpRequest.SCHEME_HTTPS.equalsIgnoreCase(scheme) || SCHEME_WSS.equalsIgnoreCase(scheme);
    }

    @FunctionalInterface
    interface ThrowingBiConsumer<T1, T2> {
        void accept(T1 t1, T2 t2) throws Exception;
    }

    /**
     * Initializes the HTTP client channel.
     */
    protected class HttpClientInitializer extends ChannelInitializer<SocketChannel> {

        final SslContext sslContext;
        final String host;
        final int port;
        final boolean stream;
        final boolean proxy;
        final boolean acceptsEvents;
        Http2SettingsHandler settingsHandler;
        private final Consumer<ChannelHandlerContext> contextConsumer;

        /**
         * @param sslContext      The ssl context
         * @param host            The host
         * @param port            The port
         * @param stream          Whether is stream
         * @param proxy           Is this a streaming proxy
         * @param acceptsEvents   Whether an event stream is accepted
         * @param contextConsumer The context consumer
         */
        protected HttpClientInitializer(
                SslContext sslContext,
                String host,
                int port,
                boolean stream,
                boolean proxy,
                boolean acceptsEvents,
                Consumer<ChannelHandlerContext> contextConsumer) {
            this.sslContext = sslContext;
            this.stream = stream;
            this.host = host;
            this.port = port;
            this.proxy = proxy;
            this.acceptsEvents = acceptsEvents;
            this.contextConsumer = contextConsumer;
        }

        /**
         * @param ch The channel
         */
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();

            Proxy proxy = configuration.resolveProxy(sslContext != null, host, port);
            if (!Proxy.NO_PROXY.equals(proxy)) {
                configureProxy(p, proxy);
            }

            if (httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {
                final Http2Connection connection = new DefaultHttp2Connection(false);
                final HttpToHttp2ConnectionHandlerBuilder builder =
                        newHttp2ConnectionHandlerBuilder(connection, configuration, stream);

                configuration.getLogLevel().ifPresent(logLevel -> {
                    try {
                        final io.netty.handler.logging.LogLevel nettyLevel = io.netty.handler.logging.LogLevel.valueOf(
                                logLevel.name()
                        );
                        builder.frameLogger(new Http2FrameLogger(nettyLevel, DefaultHttpClient.class));
                    } catch (IllegalArgumentException e) {
                        throw new HttpClientException("Unsupported log level: " + logLevel);
                    }
                });
                HttpToHttp2ConnectionHandler connectionHandler = builder
                        .build();
                if (sslContext != null) {
                    configureHttp2Ssl(this, ch, sslContext, host, port, connectionHandler);
                } else {
                    configureHttp2ClearText(this, ch, connectionHandler);
                }
            } else {
                if (stream) {
                    // for streaming responses we disable auto read
                    // so that the consumer is in charge of back pressure
                    ch.config().setAutoRead(false);
                }

                configuration.getLogLevel().ifPresent(logLevel -> {
                    try {
                        final io.netty.handler.logging.LogLevel nettyLevel = io.netty.handler.logging.LogLevel.valueOf(
                                logLevel.name()
                        );
                        p.addLast(new LoggingHandler(DefaultHttpClient.class, nettyLevel));
                    } catch (IllegalArgumentException e) {
                        throw new HttpClientException("Unsupported log level: " + logLevel);
                    }
                });

                if (sslContext != null) {
                    SslHandler sslHandler = sslContext.newHandler(ch.alloc(), host, port);
                    sslHandler.setHandshakeTimeoutMillis(configuration.getSslConfiguration().getHandshakeTimeout().toMillis());
                    p.addLast(ChannelPipelineCustomizer.HANDLER_SSL, sslHandler);
                }

                // Pool connections require alternative timeout handling
                if (poolMap == null) {
                    // read timeout settings are not applied to streamed requests.
                    // instead idle timeout settings are applied.
                    if (stream) {
                        Optional<Duration> readIdleTime = configuration.getReadIdleTimeout();
                        if (readIdleTime.isPresent()) {
                            Duration duration = readIdleTime.get();
                            if (!duration.isNegative()) {
                                p.addLast(ChannelPipelineCustomizer.HANDLER_IDLE_STATE, new IdleStateHandler(
                                        duration.toMillis(),
                                        duration.toMillis(),
                                        duration.toMillis(),
                                        TimeUnit.MILLISECONDS
                                ));
                            }
                        }
                    }
                }

                addHttp1Handlers(p);
            }
        }

        private void addHttp1Handlers(ChannelPipeline p) {
            p.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC, new HttpClientCodec());

            p.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_DECODER, new HttpContentDecompressor());

            int maxContentLength = configuration.getMaxContentLength();

            if (!stream) {
                p.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_AGGREGATOR, new HttpObjectAggregator(maxContentLength) {
                    @Override
                    protected void finishAggregation(FullHttpMessage aggregated) throws Exception {
                        if (!HttpUtil.isContentLengthSet(aggregated)) {
                            if (aggregated.content().readableBytes() > 0) {
                                super.finishAggregation(aggregated);
                            }
                        }
                    }
                });
            }
            addEventStreamHandlerIfNecessary(p);
            addFinalHandler(p);
            for (ChannelPipelineListener pipelineListener : pipelineListeners) {
                pipelineListener.onConnect(p);
            }
        }

        private void addEventStreamHandlerIfNecessary(ChannelPipeline p) {
            // if the content type is a SSE event stream we add a decoder
            // to delimit the content by lines (unless we are proxying the stream)
            if (acceptsEventStream() && !proxy) {
                p.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_EVENT_STREAM, new LineBasedFrameDecoder(configuration.getMaxContentLength(), true, true) {

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof HttpContent) {
                            if (msg instanceof LastHttpContent) {
                                super.channelRead(ctx, msg);
                            } else {
                                Attribute<Http2Stream> streamKey = ctx.channel().attr(STREAM_KEY);
                                if (msg instanceof Http2Content) {
                                    streamKey.set(((Http2Content) msg).stream());
                                }
                                try {
                                    super.channelRead(ctx, ((HttpContent) msg).content());
                                } finally {
                                    streamKey.set(null);
                                }
                            }
                        } else {
                            super.channelRead(ctx, msg);
                        }
                    }
                });

                p.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_CONTENT, new SimpleChannelInboundHandlerInstrumented<ByteBuf>(combineFactories(), false) {

                    @Override
                    public boolean acceptInboundMessage(Object msg) {
                        return msg instanceof ByteBuf;
                    }

                    @Override
                    protected void channelReadInstrumented(ChannelHandlerContext ctx, ByteBuf msg) {
                        try {
                            Attribute<Http2Stream> streamKey = ctx.channel().attr(STREAM_KEY);
                            Http2Stream http2Stream = streamKey.get();
                            if (http2Stream != null) {
                                ctx.fireChannelRead(new DefaultHttp2Content(msg.copy(), http2Stream));
                            } else {
                                ctx.fireChannelRead(new DefaultHttpContent(msg.copy()));
                            }
                        } finally {
                            msg.release();
                        }
                    }
                });

            }
        }

        /**
         * Allows overriding the final handler added to the pipeline.
         *
         * @param pipeline The pipeline
         */
        protected void addFinalHandler(ChannelPipeline pipeline) {
            pipeline.addLast(
                    ChannelPipelineCustomizer.HANDLER_HTTP_STREAM,
                    new HttpStreamsClientHandler() {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    if (evt instanceof IdleStateEvent) {
                        // close the connection if it is idle for too long
                        ctx.close();
                    }
                    super.userEventTriggered(ctx, evt);
                }

                @Override
                protected boolean isValidInMessage(Object msg) {
                    // ignore data on stream 1, that is the response to our initial upgrade request
                    return super.isValidInMessage(msg) && (sslContext != null || !discardH2cStream((HttpMessage) msg));
                }
            });
        }

        private boolean acceptsEventStream() {
            return this.acceptsEvents;
        }
    }

    /**
     * Reads the first {@link Http2Settings} object and notifies a {@link io.netty.channel.ChannelPromise}.
     */
    private final class Http2SettingsHandler extends
            SimpleChannelInboundHandlerInstrumented<Http2Settings> {
        private final ChannelPromise promise;

        /**
         * Create new instance.
         *
         * @param promise Promise object used to notify when first settings are received
         */
        Http2SettingsHandler(ChannelPromise promise) {
            super(combineFactories());
            this.promise = promise;
        }

        @Override
        protected void channelReadInstrumented(ChannelHandlerContext ctx, Http2Settings msg) {
            promise.setSuccess();

            // Only care about the first settings message
            ctx.pipeline().remove(this);
        }
    }

    /**
     * A handler that triggers the cleartext upgrade to HTTP/2 by sending an initial HTTP request.
     */
    private class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {

        private final HttpClientInitializer initializer;
        private final Http2SettingsHandler settingsHandler;

        /**
         * Default constructor.
         *
         * @param initializer The initializer
         */
        public UpgradeRequestHandler(HttpClientInitializer initializer) {
            this.initializer = initializer;
            this.settingsHandler = initializer.settingsHandler;
        }

        /**
         * @return The settings handler
         */
        public Http2SettingsHandler getSettingsHandler() {
            return settingsHandler;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            // Done with this handler, remove it from the pipeline.
            final ChannelPipeline pipeline = ctx.pipeline();

            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_SETTINGS, initializer.settingsHandler);
            DefaultFullHttpRequest upgradeRequest =
                    new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER);

            // Set HOST header as the remote peer may require it.
            InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
            String hostString = remote.getHostString();
            if (hostString == null) {
                hostString = remote.getAddress().getHostAddress();
            }
            upgradeRequest.headers().set(HttpHeaderNames.HOST, hostString + ':' + remote.getPort());
            ctx.writeAndFlush(upgradeRequest);

            ctx.fireChannelActive();
            pipeline.remove(this);
            initializer.addFinalHandler(pipeline);
        }
    }

    /**
     * Key used for connection pooling and determining host/port.
     */
    private static final class RequestKey {
        private final String host;
        private final int port;
        private final boolean secure;

        public RequestKey(URI requestURI) {
            this.secure = isSecureScheme(requestURI.getScheme());
            String host = requestURI.getHost();
            int port;
            if (host == null) {
                host = requestURI.getAuthority();
                if (host == null) {
                    throw new NoHostException("URI specifies no host to connect to");
                }

                final int i = host.indexOf(':');
                if (i > -1) {
                    final String portStr = host.substring(i + 1);
                    host = host.substring(0, i);
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        throw new HttpClientException("URI specifies an invalid port: " + portStr);
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
            return Objects.hash(host, port, secure);
        }
    }

    /**
     * A Netty request writer.
     */
    protected class NettyRequestWriter {

        private final HttpRequest nettyRequest;
        private final HttpPostRequestEncoder encoder;
        private final String scheme;
        private final boolean closeChannelAfterWrite;

        /**
         * @param scheme                 The scheme
         * @param nettyRequest           The Netty request
         * @param encoder                The encoder
         * @param closeChannelAfterWrite Whether to close the after write
         */
        NettyRequestWriter(String scheme, HttpRequest nettyRequest, HttpPostRequestEncoder encoder, boolean closeChannelAfterWrite) {
            this.nettyRequest = nettyRequest;
            this.encoder = encoder;
            this.scheme = scheme;
            this.closeChannelAfterWrite = closeChannelAfterWrite;
        }

        /**
         * @param channel     The channel
         * @param channelPool The channel pool
         * @param emitter     The emitter
         */
        protected void writeAndClose(Channel channel, ChannelPool channelPool, FluxSink<?> emitter) {
            final ChannelPipeline pipeline = channel.pipeline();
            if (httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {
                final boolean isSecure = sslContext != null && isSecureScheme(scheme);
                if (isSecure) {
                    nettyRequest.headers().add(AbstractNettyHttpRequest.HTTP2_SCHEME, HttpScheme.HTTPS);
                } else {
                    nettyRequest.headers().add(AbstractNettyHttpRequest.HTTP2_SCHEME, HttpScheme.HTTP);
                }

                // for HTTP/2 over cleartext we have to wait for the protocol upgrade to complete
                // so we get the Http2SettingsHandler and await receiving the Http2Settings object
                // which indicates the protocol negotiation has completed successfully
                final UpgradeRequestHandler upgradeRequestHandler =
                        (UpgradeRequestHandler) pipeline.get(ChannelPipelineCustomizer.HANDLER_HTTP2_UPGRADE_REQUEST);
                final Http2SettingsHandler settingsHandler;
                if (upgradeRequestHandler != null) {
                    settingsHandler = upgradeRequestHandler.getSettingsHandler();
                } else {
                    // upgrade request already received to handler must have been removed
                    // therefore the Http2SettingsHandler is in the pipeline
                    settingsHandler = (Http2SettingsHandler) pipeline.get(ChannelPipelineCustomizer.HANDLER_HTTP2_SETTINGS);
                }
                // if the settings handler is null and no longer in the pipeline, fall through
                // since this means the HTTP/2 clear text upgrade completed, otherwise
                // add a listener to the future that writes once the upgrade completes
                if (settingsHandler != null) {
                    addInstrumentedListener(settingsHandler.promise, future -> {
                        if (future.isSuccess()) {
                            processRequestWrite(channel, channelPool, emitter, pipeline);
                        } else {
                            throw new HttpClientException("HTTP/2 clear text upgrade failed to complete", future.cause());
                        }
                    });
                    return;
                }
            }
            processRequestWrite(channel, channelPool, emitter, pipeline);
        }

        private void processRequestWrite(Channel channel, ChannelPool channelPool, FluxSink<?> emitter, ChannelPipeline pipeline) {
            ChannelFuture channelFuture;
            if (encoder != null && encoder.isChunked()) {
                channel.attr(AttributeKey.valueOf(ChannelPipelineCustomizer.HANDLER_HTTP_CHUNK)).set(true);
                pipeline.addAfter(ChannelPipelineCustomizer.HANDLER_HTTP_STREAM, ChannelPipelineCustomizer.HANDLER_HTTP_CHUNK, new ChunkedWriteHandler());
                channel.write(nettyRequest);
                channelFuture = channel.writeAndFlush(encoder);
            } else {
                channelFuture = channel.writeAndFlush(nettyRequest);
            }

            if (channelPool != null) {
                closeChannelIfNecessary(channel, emitter, channelFuture, false);
            } else {
                closeChannelIfNecessary(channel, emitter, channelFuture, closeChannelAfterWrite);
            }
        }

        private void closeChannelIfNecessary(
                Channel channel,
                FluxSink<?> emitter,
                ChannelFuture channelFuture,
                boolean closeChannelAfterWrite) {
            addInstrumentedListener(channelFuture, f -> {
                try {
                    if (!f.isSuccess()) {
                        if (!emitter.isCancelled()) {
                            emitter.error(f.cause());
                        }
                    } else {
                        // reset to read mode
                        channel.read();
                    }
                } finally {
                    if (encoder != null) {
                        encoder.cleanFiles();
                    }
                    channel.attr(AttributeKey.valueOf(ChannelPipelineCustomizer.HANDLER_HTTP_CHUNK)).set(null);
                    if (closeChannelAfterWrite) {
                        closeChannelAsync(channel);
                    }
                }
            });
        }

        /**
         * @return The Netty request
         */
        HttpRequest getNettyRequest() {
            return nettyRequest;
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

    private abstract class BaseHttpResponseHandler<R extends io.netty.handler.codec.http.HttpResponse, O> extends SimpleChannelInboundHandlerInstrumented<R> {
        private final Promise<O> responsePromise;
        private final io.micronaut.http.HttpRequest<?> parentRequest;
        private final io.micronaut.http.HttpRequest<?> finalRequest;

        public BaseHttpResponseHandler(Promise<O> responsePromise, io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<?> finalRequest) {
            super(combineFactories());
            this.responsePromise = responsePromise;
            this.parentRequest = parentRequest;
            this.finalRequest = finalRequest;
        }

        @Override
        public abstract boolean acceptInboundMessage(Object msg);

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
                result = (new ContentLengthExceededException(configuration.getMaxContentLength()));
            } else if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
                result = ReadTimeoutException.TIMEOUT_EXCEPTION;
            } else {
                result = new HttpClientException("Error occurred reading HTTP response: " + message, cause);
            }
            responsePromise.tryFailure(result);
        }

        @Override
        protected void channelReadInstrumented(ChannelHandlerContext ctx, R msg) throws Exception {
            if (responsePromise.isDone()) {
                return;
            }

            if (log.isDebugEnabled()) {
                log.debug("Received response {} from {}", msg.status().code(), finalRequest.getUri());
            }

            int code = msg.status().code();
            HttpHeaders headers1 = msg.headers();
            if (code > 300 && code < 400 && configuration.isFollowRedirects() && headers1.contains(HttpHeaderNames.LOCATION)) {
                String location = headers1.get(HttpHeaderNames.LOCATION);

                MutableHttpRequest<Object> redirectRequest;
                if (code == 307) {
                    redirectRequest = io.micronaut.http.HttpRequest.create(finalRequest.getMethod(), location);
                    finalRequest.getBody().ifPresent(redirectRequest::body);
                } else {
                    redirectRequest = io.micronaut.http.HttpRequest.GET(location);
                }

                setRedirectHeaders(finalRequest, redirectRequest);
                Flux.from(resolveRedirectURI(parentRequest, redirectRequest))
                        .flatMap(makeRedirectHandler(parentRequest, redirectRequest))
                        .subscribe(new NettyPromiseSubscriber<>(responsePromise));
                return;
            }

            HttpResponseStatus status = msg.status();
            int statusCode = status.code();
            HttpStatus httpStatus;
            try {
                httpStatus = HttpStatus.valueOf(statusCode);
            } catch (IllegalArgumentException e) {
                responsePromise.tryFailure(e);
                return;
            }

            HttpHeaders headers = msg.headers();
            if (log.isTraceEnabled()) {
                log.trace("HTTP Client Streaming Response Received ({}) for Request: {} {}", msg.status(), finalRequest.getMethodName(), finalRequest.getUri());
                traceHeaders(headers);
            }
            buildResponse(responsePromise, msg, httpStatus);
        }

        private void setRedirectHeaders(@Nullable io.micronaut.http.HttpRequest<?> request, MutableHttpRequest<Object> redirectRequest) {
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

        protected abstract Function<URI, Publisher<? extends O>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest);

        protected abstract void buildResponse(Promise<? super O> promise, R msg, HttpStatus httpStatus);
    }

    private class FullHttpResponseHandler<O> extends BaseHttpResponseHandler<FullHttpResponse, HttpResponse<O>> {
        private final boolean secure;
        private final Argument<O> bodyType;
        private final Argument<?> errorType;
        private final ChannelPool channelPool;

        private boolean keepAlive = true;

        public FullHttpResponseHandler(
                Promise<HttpResponse<O>> responsePromise,
                ChannelPool channelPool,
                boolean secure,
                io.micronaut.http.HttpRequest<?> request,
                Argument<O> bodyType,
                Argument<?> errorType) {
            super(responsePromise, request, request);
            this.secure = secure;
            this.bodyType = bodyType;
            this.errorType = errorType;
            this.channelPool = channelPool;
        }

        @Override
        public boolean acceptInboundMessage(Object msg) {
            return msg instanceof FullHttpResponse && (secure || !discardH2cStream((HttpMessage) msg));
        }

        @Override
        protected Function<URI, Publisher<? extends HttpResponse<O>>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest) {
            return uri -> exchangeImpl(uri, parentRequest, redirectRequest, bodyType, errorType);
        }

        @Override
        protected void channelReadInstrumented(ChannelHandlerContext channelHandlerContext, FullHttpResponse fullResponse) throws Exception {
            try {
                // corresponding release is the SimpleChannelInboundHandler autoRelease
                // this should probably be dropped at some point
                fullResponse.retain();
                super.channelReadInstrumented(channelHandlerContext, fullResponse);
            } finally {
                // leave one reference for SimpleChannelInboundHandler autoRelease
                if (fullResponse.refCnt() > 1) {
                    try {
                        ReferenceCountUtil.release(fullResponse);
                    } catch (Exception e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Failed to release response: {}", fullResponse);
                        }
                    }
                }
                if (!HttpUtil.isKeepAlive(fullResponse)) {
                    keepAlive = false;
                }
                channelHandlerContext.pipeline().remove(this);
            }
        }

        @Override
        protected void buildResponse(Promise<? super HttpResponse<O>> promise, FullHttpResponse msg, HttpStatus httpStatus) {
            try {
                if (httpStatus == HttpStatus.NO_CONTENT) {
                    // normalize the NO_CONTENT header, since http content aggregator adds it even if not present in the response
                    msg.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                }

                boolean convertBodyWithBodyType = httpStatus.getCode() < 400 ||
                        (!DefaultHttpClient.this.configuration.isExceptionOnErrorStatus() && bodyType.equalsType(errorType));
                FullNettyClientHttpResponse<O> response
                        = new FullNettyClientHttpResponse<>(msg, httpStatus, mediaTypeCodecRegistry, byteBufferFactory, bodyType, convertBodyWithBodyType);

                if (convertBodyWithBodyType) {
                    promise.trySuccess(response);
                    response.onComplete();
                } else { // error flow
                    try {
                        promise.tryFailure(makeErrorFromRequestBody(msg.status(), response));
                        response.onComplete();
                    } catch (HttpClientResponseException t) {
                        promise.tryFailure(t);
                        response.onComplete();
                    } catch (Exception t) {
                        response.onComplete();
                        promise.tryFailure(makeErrorBodyParseError(msg, httpStatus, t));
                    }
                }
            } catch (HttpClientResponseException t) {
                promise.tryFailure(t);
            } catch (Exception t) {
                makeNormalBodyParseError(msg, httpStatus, t, cause -> {
                    if (!promise.tryFailure(cause) && log.isWarnEnabled()) {
                        log.warn("Exception fired after handler completed: " + t.getMessage(), t);
                    }
                });
            }
        }

        /**
         * Create a {@link HttpClientResponseException} from a response with a failed HTTP status.
         */
        private HttpClientResponseException makeErrorFromRequestBody(HttpResponseStatus status, FullNettyClientHttpResponse<?> response) {
            if (errorType != null && errorType != HttpClient.DEFAULT_ERROR_TYPE) {
                return new HttpClientResponseException(
                        status.reasonPhrase(),
                        null,
                        response,
                        new HttpClientErrorDecoder() {
                            @Override
                            public Argument<?> getErrorType(MediaType mediaType) {
                                return errorType;
                            }
                        }
                );
            } else {
                return new HttpClientResponseException(status.reasonPhrase(), response);
            }
        }

        /**
         * Create a {@link HttpClientResponseException} if parsing of the HTTP error body failed.
         */
        private HttpClientResponseException makeErrorBodyParseError(FullHttpResponse fullResponse, HttpStatus httpStatus, Throwable t) {
            FullNettyClientHttpResponse<Object> errorResponse = new FullNettyClientHttpResponse<>(
                    fullResponse,
                    httpStatus,
                    mediaTypeCodecRegistry,
                    byteBufferFactory,
                    null,
                    false
            );
            // this onComplete call disables further parsing by HttpClientResponseException
            errorResponse.onComplete();
            return new HttpClientResponseException(
                    "Error decoding HTTP error response body: " + t.getMessage(),
                    t,
                    errorResponse,
                    null
            );
        }

        private void makeNormalBodyParseError(FullHttpResponse fullResponse, HttpStatus httpStatus, Throwable t, Consumer<HttpClientResponseException> forward) {
            FullNettyClientHttpResponse<Object> response = new FullNettyClientHttpResponse<>(
                    fullResponse,
                    httpStatus,
                    mediaTypeCodecRegistry,
                    byteBufferFactory,
                    null,
                    false
            );
            HttpClientResponseException clientResponseError = new HttpClientResponseException(
                    "Error decoding HTTP response body: " + t.getMessage(),
                    t,
                    response,
                    new HttpClientErrorDecoder() {
                        @Override
                        public Argument<?> getErrorType(MediaType mediaType) {
                            return errorType;
                        }
                    }
            );
            try {
                forward.accept(clientResponseError);
            } finally {
                response.onComplete();
            }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            if (channelPool != null) {
                removeReadTimeoutHandler(ctx.pipeline());
                final Channel ch = ctx.channel();
                if (!keepAlive) {
                    ch.closeFuture().addListener((future ->
                            channelPool.release(ch)
                    ));
                } else {
                    channelPool.release(ch);
                }
            } else {
                // just close it to prevent any future reads without a handler registered
                ctx.close();
            }
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            addReadTimeoutHandler(ctx.pipeline());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            super.exceptionCaught(ctx, cause);
            keepAlive = false;
            ctx.pipeline().remove(this);
        }
    }

    private class StreamFullHttpResponseHandler extends BaseHttpResponseHandler<FullHttpResponse, HttpResponse<?>> {
        public StreamFullHttpResponseHandler(Promise<HttpResponse<?>> responsePromise, io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<?> finalRequest) {
            super(responsePromise, parentRequest, finalRequest);
        }

        @Override
        public boolean acceptInboundMessage(Object msg) {
            return msg instanceof FullHttpResponse;
        }

        @Override
        protected void buildResponse(Promise<? super HttpResponse<?>> promise, FullHttpResponse msg, HttpStatus httpStatus) {
            Publisher<HttpContent> bodyPublisher;
            if (msg.content() instanceof EmptyByteBuf) {
                bodyPublisher = Publishers.empty();
            } else {
                bodyPublisher = Publishers.just(new DefaultLastHttpContent(msg.content()));
            }
            DefaultStreamedHttpResponse nettyResponse = new DefaultStreamedHttpResponse(
                    msg.protocolVersion(),
                    msg.status(),
                    msg.headers(),
                    bodyPublisher
            );
            promise.trySuccess(new NettyStreamedHttpResponse<>(nettyResponse, httpStatus));
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            super.handlerAdded(ctx);
            addReadTimeoutHandler(ctx.pipeline());
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            super.handlerRemoved(ctx);
            removeReadTimeoutHandler(ctx.pipeline());
        }

        @Override
        protected Function<URI, Publisher<? extends HttpResponse<?>>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest) {
            return uri -> buildStreamExchange(parentRequest, redirectRequest, uri, null);
        }
    }

    private class StreamStreamHttpResponseHandler extends BaseHttpResponseHandler<StreamedHttpResponse, HttpResponse<?>> {
        public StreamStreamHttpResponseHandler(Promise<HttpResponse<?>> responsePromise, io.micronaut.http.HttpRequest<?> parentRequest, io.micronaut.http.HttpRequest<?> finalRequest) {
            super(responsePromise, parentRequest, finalRequest);
        }

        @Override
        public boolean acceptInboundMessage(Object msg) {
            return msg instanceof StreamedHttpResponse;
        }

        @Override
        protected void buildResponse(Promise<? super HttpResponse<?>> promise, StreamedHttpResponse msg, HttpStatus httpStatus) {
            promise.trySuccess(new NettyStreamedHttpResponse<>(msg, httpStatus));
        }

        @Override
        protected Function<URI, Publisher<? extends HttpResponse<?>>> makeRedirectHandler(io.micronaut.http.HttpRequest<?> parentRequest, MutableHttpRequest<Object> redirectRequest) {
            return uri -> buildStreamExchange(parentRequest, redirectRequest, uri, null);
        }
    }
}
