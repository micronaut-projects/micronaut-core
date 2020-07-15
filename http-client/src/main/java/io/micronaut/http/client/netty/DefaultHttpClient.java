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
package io.micronaut.http.client.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.Internal;
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
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.*;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.client.*;
import io.micronaut.http.client.exceptions.*;
import io.micronaut.http.client.filter.ClientFilterResolutionContext;
import io.micronaut.http.client.filter.DefaultHttpClientFilterResolver;
import io.micronaut.http.client.filters.ClientServerContextFilter;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.multipart.MultipartDataFactory;
import io.micronaut.http.client.sse.RxSseClient;
import io.micronaut.http.client.netty.ssl.NettyClientSslBuilder;
import io.micronaut.http.client.netty.websocket.NettyWebSocketClientHandler;
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
import io.micronaut.http.netty.content.HttpContentUtil;
import io.micronaut.http.netty.stream.HttpStreamsClientHandler;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.http.netty.stream.StreamingInboundHttp2ToHttpAdapter;
import io.micronaut.http.sse.Event;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.jackson.ObjectMapperFactory;
import io.micronaut.jackson.codec.JsonMediaTypeCodec;
import io.micronaut.jackson.codec.JsonStreamMediaTypeCodec;
import io.micronaut.jackson.parser.JacksonProcessor;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.websocket.RxWebSocketClient;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.context.WebSocketBean;
import io.micronaut.websocket.context.WebSocketBeanRegistry;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
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
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Cancellable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.Proxy.Type;
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
import java.util.function.Supplier;

/**
 * Default implementation of the {@link HttpClient} interface based on Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultHttpClient implements
        RxWebSocketClient,
        RxHttpClient,
        RxStreamingHttpClient,
        RxSseClient,
        RxProxyHttpClient,
        ChannelPipelineCustomizer,
        Closeable,
        AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClient.class);
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

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
    private final Collection<ChannelPipelineListener> pipelineListeners = new ArrayList<>(2);

    /**
     * Construct a client for the given arguments.
     *
     * @param loadBalancer               The {@link LoadBalancer} to use for selecting servers
     * @param configuration              The {@link HttpClientConfiguration} object
     * @param contextPath                The base URI to prepend to request uris
     * @param threadFactory              The thread factory to use for client threads
     * @param nettyClientSslBuilder      The SSL builder
     * @param codecRegistry              The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param annotationMetadataResolver The annotation metadata resolver
     * @param filters                    The filters to use
     */
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer,
                             HttpClientConfiguration configuration,
                             @Nullable String contextPath,
                             @Nullable ThreadFactory threadFactory,
                             NettyClientSslBuilder nettyClientSslBuilder,
                             MediaTypeCodecRegistry codecRegistry,
                             @Nullable AnnotationMetadataResolver annotationMetadataResolver,
                             HttpClientFilter... filters) {
        this(loadBalancer, io.micronaut.http.HttpVersion.HTTP_1_1, configuration, contextPath, new DefaultHttpClientFilterResolver(annotationMetadataResolver, Arrays.asList(filters)), null, threadFactory, nettyClientSslBuilder, codecRegistry, WebSocketBeanRegistry.EMPTY, new DefaultRequestBinderRegistry(ConversionService.SHARED), null, NioSocketChannel.class);
    }

    /**
     * Construct a client for the given arguments.
     *
     * @param loadBalancer          The {@link LoadBalancer} to use for selecting servers
     * @param httpVersion           The HTTP version to use. Can be null and defaults to {@link io.micronaut.http.HttpVersion#HTTP_1_1}
     * @param configuration         The {@link HttpClientConfiguration} object
     * @param contextPath           The base URI to prepend to request uris
     * @param filterResolver        The http client filter resolver
     * @param clientFilterEntries   The client filter entries
     * @param threadFactory         The thread factory to use for client threads
     * @param nettyClientSslBuilder The SSL builder
     * @param codecRegistry         The {@link MediaTypeCodecRegistry} to use for encoding and decoding objects
     * @param webSocketBeanRegistry The websocket bean registry
     * @param requestBinderRegistry The request binder registry
     * @param eventLoopGroup        The event loop group to use
     * @param socketChannelClass    The socket channel class
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
                             @NonNull Class<? extends SocketChannel> socketChannelClass) {
        ArgumentUtils.requireNonNull("nettyClientSslBuilder", nettyClientSslBuilder);
        ArgumentUtils.requireNonNull("codecRegistry", codecRegistry);
        ArgumentUtils.requireNonNull("webSocketBeanRegistry", webSocketBeanRegistry);
        ArgumentUtils.requireNonNull("requestBinderRegistry", requestBinderRegistry);
        ArgumentUtils.requireNonNull("configuration", configuration);
        ArgumentUtils.requireNonNull("filterResolver", filterResolver);
        ArgumentUtils.requireNonNull("socketChannelClass", socketChannelClass);
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
        this.scheduler = Schedulers.from(group);
        this.threadFactory = threadFactory;
        this.bootstrap.group(group)
                .channel(socketChannelClass)
                .option(ChannelOption.SO_KEEPALIVE, true);

        Optional<Duration> readTimeout = configuration.getReadTimeout();
        this.readTimeoutMillis = readTimeout.map(duration -> !duration.isNegative() ? duration.toMillis() : null).orElse(null);

        Optional<Duration> connectTtl = configuration.getConnectTtl();
        this.connectionTimeAliveMillis = connectTtl.map(duration -> !duration.isNegative() ? duration.toMillis() : null).orElse(null);

        HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration = configuration.getConnectionPoolConfiguration();
        // HTTP/2 defaults to keep alive connections so should we should always use a pool
        if (connectionPoolConfiguration.isEnabled() || this.httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {
            int maxConnections = connectionPoolConfiguration.getMaxConnections();
            if (maxConnections > -1) {
                poolMap = new AbstractChannelPoolMap<RequestKey, ChannelPool>() {
                    @Override
                    protected ChannelPool newPool(RequestKey key) {
                        Bootstrap newBootstrap = bootstrap.clone(group);
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
        this.log = configuration.getLoggerName().map(LoggerFactory::getLogger).orElse(LOG);
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
    }

    /**
     * @param url The URL
     */
    public DefaultHttpClient(URL url) {
        this(url, new DefaultHttpClientConfiguration());
    }

    /**
     *
     */
    public DefaultHttpClient() {
        this((LoadBalancer) null, new DefaultHttpClientConfiguration());
    }

    /**
     * @param url           The URL
     * @param configuration The {@link HttpClientConfiguration} object
     */
    public DefaultHttpClient(URL url, HttpClientConfiguration configuration) {
        this(
                url == null ? null : LoadBalancer.fixed(url), configuration, null, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
                new NettyClientSslBuilder(new ResourceResolver()), createDefaultMediaTypeRegistry(), AnnotationMetadataResolver.DEFAULT);
    }

    /**
     * @param loadBalancer  The {@link LoadBalancer} to use for selecting servers
     * @param configuration The {@link HttpClientConfiguration} object
     */
    public DefaultHttpClient(@Nullable LoadBalancer loadBalancer, HttpClientConfiguration configuration) {
        this(loadBalancer,
                configuration, null, new DefaultThreadFactory(MultithreadEventLoopGroup.class),
                new NettyClientSslBuilder(new ResourceResolver()),
                createDefaultMediaTypeRegistry(), AnnotationMetadataResolver.DEFAULT);
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
                            ((SimpleChannelPool) cp).closeAsync().addListener(future -> {
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
                Duration shutdownTimeout = configuration.getShutdownTimeout().orElse(Duration.ofMillis(100));

                Future<?> future = this.group.shutdownGracefully(
                        1,
                        shutdownTimeout.toMillis(),
                        TimeUnit.MILLISECONDS
                );
                future.addListener(f -> {
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
                Flowable<io.micronaut.http.HttpResponse<O>> publisher = DefaultHttpClient.this.exchange(request, bodyType, errorType);
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
                }).blockingFirst();
            }
        };
    }

    @SuppressWarnings("SubscriberImplementation")
    @Override
    public <I> Flowable<Event<ByteBuffer<?>>> eventStream(io.micronaut.http.HttpRequest<I> request) {

        if (request instanceof MutableHttpRequest) {
            ((MutableHttpRequest) request).accept(MediaType.TEXT_EVENT_STREAM_TYPE);
        }

        return Flowable.create(emitter ->
                dataStream(request).subscribe(new Subscriber<ByteBuffer<?>>() {
                    private Subscription dataSubscription;
                    private CurrentEvent currentEvent;

                    @Override
                    public void onSubscribe(Subscription s) {
                        this.dataSubscription = s;
                        Cancellable cancellable = () -> dataSubscription.cancel();
                        emitter.setCancellable(cancellable);
                        if (!emitter.isCancelled() && emitter.requested() > 0) {
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
                                    emitter.onNext(
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

                            if (emitter.requested() > 0 && !emitter.isCancelled()) {
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
                            emitter.onError(t);
                        } else {
                            emitter.onError(new HttpClientException("Error consuming Server Sent Events: " + t.getMessage(), t));
                        }
                    }

                    @Override
                    public void onComplete() {
                        emitter.onComplete();
                    }
                }), BackpressureStrategy.BUFFER);
    }

    @Override
    public <I, B> Flowable<Event<B>> eventStream(io.micronaut.http.HttpRequest<I> request, Argument<B> eventType) {
        return eventStream(request).map(byteBufferEvent -> {
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
    public <I> Flowable<ByteBuffer<?>> dataStream(io.micronaut.http.HttpRequest<I> request) {
        return Flowable.fromPublisher(resolveRequestURI(request))
                .flatMap(buildDataStreamPublisher(request));

    }

    @Override
    public <I> Flowable<io.micronaut.http.HttpResponse<ByteBuffer<?>>> exchangeStream(io.micronaut.http.HttpRequest<I> request) {
        return Flowable.fromPublisher(resolveRequestURI(request))
                .flatMap(buildExchangeStreamPublisher(request));
    }

    @Override
    public <I, O> Flowable<O> jsonStream(io.micronaut.http.HttpRequest<I> request, io.micronaut.core.type.Argument<O> type) {
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return Flowable.fromPublisher(resolveRequestURI(request))
                .flatMap(buildJsonStreamPublisher(parentRequest, request, type));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I> Flowable<Map<String, Object>> jsonStream(io.micronaut.http.HttpRequest<I> request) {
        return (Flowable) jsonStream(request, Map.class);
    }

    @Override
    public <I, O> Flowable<O> jsonStream(io.micronaut.http.HttpRequest<I> request, Class<O> type) {
        return jsonStream(request, io.micronaut.core.type.Argument.of(type));
    }

    @Override
    public <I, O, E> Flowable<io.micronaut.http.HttpResponse<O>> exchange(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        Publisher<URI> uriPublisher = resolveRequestURI(request);
        return Flowable.fromPublisher(uriPublisher)
                .switchMap(buildExchangePublisher(parentRequest, request, bodyType, errorType));
    }

    @Override
    public <T extends AutoCloseable> Flowable<T> connect(Class<T> clientEndpointType, io.micronaut.http.MutableHttpRequest<?> request) {
        Publisher<URI> uriPublisher = resolveRequestURI(request);
        return Flowable.fromPublisher(uriPublisher)
                .switchMap(resolvedURI -> connectWebSocket(resolvedURI, request, clientEndpointType, null));
    }

    @Override
    public <T extends AutoCloseable> Flowable<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters) {
        WebSocketBean<T> webSocketBean = webSocketRegistry.getWebSocket(clientEndpointType);
        String uri = webSocketBean.getBeanDefinition().stringValue(ClientWebSocket.class).orElse("/ws");
        uri = UriTemplate.of(uri).expand(parameters);
        MutableHttpRequest<Object> request = io.micronaut.http.HttpRequest.GET(uri);
        Publisher<URI> uriPublisher = resolveRequestURI(request);

        return Flowable.fromPublisher(uriPublisher)
                .switchMap(resolvedURI -> connectWebSocket(resolvedURI, request, clientEndpointType, webSocketBean));

    }

    @Override
    public void close() {
        stop();
    }

    private <I, O, E> Flowable<io.micronaut.http.HttpResponse<O>> redirectExchange(io.micronaut.http.HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        Publisher<URI> uriPublisher = resolveRequestURI(request, false);
        return Flowable.fromPublisher(uriPublisher)
                .switchMap(buildExchangePublisher(parentRequest, request, bodyType, errorType));
    }

    private <T> Flowable<T> connectWebSocket(URI uri, MutableHttpRequest<?> request, Class<T> clientEndpointType, WebSocketBean<T> webSocketBean) {
        Bootstrap bootstrap = this.bootstrap.clone();
        if (webSocketBean == null) {
            webSocketBean = webSocketRegistry.getWebSocket(clientEndpointType);
        }

        WebSocketBean<T> finalWebSocketBean = webSocketBean;
        return Flowable.create(emitter -> {
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
                emitter.onError(e);
                return;
            }

            bootstrap.remoteAddress(requestKey.getHost(), requestKey.getPort());
            bootstrap.handler(new HttpClientInitializer(
                    sslContext,
                    requestKey.getHost(),
                    requestKey.getPort(),
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
                        URI webSocketURL = URI.create("ws://" + host + ":" + port + uri.getPath());

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
                                        webSocketURL, protocolVersion, subprotocol, false, customHeaders, maxFramePayloadLength),
                                requestBinderRegistry,
                                mediaTypeCodecRegistry,
                                emitter);
                        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_WEBSOCKET_CLIENT, webSocketHandler);
                    } catch (Throwable e) {
                        emitter.onError(new WebSocketSessionException("Error opening WebSocket client session: " + e.getMessage(), e));
                    }
                }
            });

            bootstrap.connect().addListener(future -> {
                if (!future.isSuccess()) {
                    emitter.onError(future.cause());
                }
            });
        }, BackpressureStrategy.ERROR);
    }

    /**
     * @param request The request
     * @param <I>     The input type
     * @return A {@link Function}
     */
    protected <I> Function<URI, Flowable<io.micronaut.http.HttpResponse<ByteBuffer<?>>>> buildExchangeStreamPublisher(io.micronaut.http.HttpRequest<I> request) {
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return requestURI -> {
            Flowable<io.micronaut.http.HttpResponse<Object>> streamResponsePublisher = buildStreamExchange(parentRequest, request, requestURI);
            return streamResponsePublisher.switchMap(response -> {
                StreamedHttpResponse streamedHttpResponse = NettyHttpResponseBuilder.toStreamResponse(response);
                Flowable<HttpContent> httpContentFlowable = Flowable.fromPublisher(streamedHttpResponse);
                return httpContentFlowable
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
                        }).doAfterNext(res -> {
                            ByteBuffer<?> buffer = res.body();
                            if (buffer instanceof ReferenceCounted) {
                                ((ReferenceCounted) buffer).release();
                            }
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
        };
    }

    /**
     * @param parentRequest The parent request
     * @param request       The request
     * @param type          The type
     * @param <I>           The input type
     * @param <O>           The output type
     * @return A {@link Function}
     */
    protected <I, O> Function<URI, Flowable<O>> buildJsonStreamPublisher(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            io.micronaut.core.type.Argument<O> type) {
        return requestURI -> {
            Flowable<io.micronaut.http.HttpResponse<Object>> streamResponsePublisher =
                    buildStreamExchange(parentRequest, request, requestURI);
            return streamResponsePublisher.switchMap(response -> {
                if (!(response instanceof NettyStreamedHttpResponse)) {
                    throw new IllegalStateException("Response has been wrapped in non streaming type. Do not wrap the response in client filters for stream requests");
                }

                JsonMediaTypeCodec mediaTypeCodec = (JsonMediaTypeCodec) mediaTypeCodecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE)
                        .orElseThrow(() -> new IllegalStateException("No JSON codec found"));

                StreamedHttpResponse streamResponse = NettyHttpResponseBuilder.toStreamResponse(response);
                Flowable<HttpContent> httpContentFlowable = Flowable.fromPublisher(streamResponse);

                boolean isJsonStream = response.getContentType().map(mediaType -> mediaType.equals(MediaType.APPLICATION_JSON_STREAM_TYPE)).orElse(false);
                boolean streamArray = !Iterable.class.isAssignableFrom(type.getType()) && !isJsonStream;
                JacksonProcessor jacksonProcessor = new JacksonProcessor(mediaTypeCodec.getObjectMapper().getFactory(), streamArray, mediaTypeCodec.getObjectMapper().getDeserializationConfig()) {
                    @Override
                    public void subscribe(Subscriber<? super JsonNode> downstreamSubscriber) {
                        httpContentFlowable.map(content -> {
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
                        }).subscribe(this);
                        super.subscribe(downstreamSubscriber);
                    }
                };
                return Flowable.fromPublisher(jacksonProcessor).map(jsonNode ->
                        mediaTypeCodec.decode(type, jsonNode)
                );
            }).doOnTerminate(() -> {
                final Object o = request.getAttribute(NettyClientHttpRequest.CHANNEL).orElse(null);
                if (o instanceof Channel) {
                    final Channel c = (Channel) o;
                    if (c.isOpen()) {
                        c.close();
                    }
                }
            });
        };
    }

    /**
     * @param request The request
     * @param <I>     The input type
     * @return A {@link Function}
     */
    protected <I> Function<URI, Flowable<ByteBuffer<?>>> buildDataStreamPublisher(io.micronaut.http.HttpRequest<I> request) {
        final io.micronaut.http.HttpRequest<Object> parentRequest = ServerRequestContext.currentRequest().orElse(null);
        return requestURI -> {
            Flowable<io.micronaut.http.HttpResponse<Object>> streamResponsePublisher = buildStreamExchange(parentRequest, request, requestURI);
            Function<HttpContent, ByteBuffer<?>> contentMapper = message -> {
                ByteBuf byteBuf = message.content();
                return byteBufferFactory.wrap(byteBuf);
            };
            return streamResponsePublisher.switchMap(response -> {
                if (!(response instanceof NettyStreamedHttpResponse)) {
                    throw new IllegalStateException("Response has been wrapped in non streaming type. Do not wrap the response in client filters for stream requests");
                }
                NettyStreamedHttpResponse nettyStreamedHttpResponse = (NettyStreamedHttpResponse) response;
                Flowable<HttpContent> httpContentFlowable = Flowable.fromPublisher(nettyStreamedHttpResponse.getNettyResponse());
                return httpContentFlowable.filter(message -> !(message.content() instanceof EmptyByteBuf)).map(contentMapper);
            }).doOnTerminate(() -> {
                final Object o = request.getAttribute(NettyClientHttpRequest.CHANNEL).orElse(null);
                if (o instanceof Channel) {
                    final Channel c = (Channel) o;
                    if (c.isOpen()) {
                        c.close();
                    }
                }
            });
        };
    }

    /**
     * @param parentRequest The parent request
     * @param request       The request
     * @param requestURI    The request URI
     * @param <I>           The input type
     * @return A {@link Flowable}
     */
    @SuppressWarnings("MagicNumber")
    protected <I> Flowable<io.micronaut.http.HttpResponse<Object>> buildStreamExchange(
            @Nullable io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI) {
        SslContext sslContext = buildSslContext(requestURI);

        AtomicReference<io.micronaut.http.HttpRequest> requestWrapper = new AtomicReference<>(request);
        Flowable<io.micronaut.http.HttpResponse<Object>> streamResponsePublisher = Flowable.create(emitter -> {

            ChannelFuture channelFuture;
            try {
                if (httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {

                    channelFuture = doConnect(request, requestURI, sslContext, true, channelHandlerContext -> {
                        try {
                            final Channel channel = channelHandlerContext.channel();
                            request.setAttribute(NettyClientHttpRequest.CHANNEL, channel);
                            streamRequestThroughChannel(
                                    parentRequest,
                                    requestURI,
                                    requestWrapper,
                                    emitter,
                                    channel,
                                    true
                            );
                        } catch (Throwable e) {
                            emitter.onError(e);
                        }
                    });
                } else {
                    channelFuture = doConnect(request, requestURI, sslContext, true, null);
                    channelFuture
                            .addListener((ChannelFutureListener) f -> {
                                if (f.isSuccess()) {
                                    Channel channel = f.channel();
                                    request.setAttribute(NettyClientHttpRequest.CHANNEL, channel);
                                    streamRequestThroughChannel(
                                            parentRequest,
                                            requestURI,
                                            requestWrapper,
                                            emitter,
                                            channel,
                                            true
                                    );
                                } else {
                                    Throwable cause = f.cause();
                                    emitter.onError(
                                            new HttpClientException("Connect error:" + cause.getMessage(), cause)
                                    );
                                }
                            });
                }
            } catch (HttpClientException e) {
                emitter.onError(e);
                return;
            }

            Disposable disposable = buildDisposableChannel(channelFuture);
            emitter.setDisposable(disposable);
            emitter.setCancellable(disposable::dispose);
        }, BackpressureStrategy.BUFFER);

        // apply filters
        streamResponsePublisher = Flowable.fromPublisher(
                applyFilterToResponsePublisher(parentRequest, request, requestURI, requestWrapper, streamResponsePublisher)
        );

        return streamResponsePublisher.subscribeOn(scheduler);
    }

    /**
     * @param <I>           The input type
     * @param <O>           The output type
     * @param <E>           The error type
     * @param parentRequest The parent request
     * @param request       The request
     * @param bodyType      The body type
     * @param errorType     The error type
     * @return A {@link Function}
     */
    protected <I, O, E> Function<URI, Publisher<? extends io.micronaut.http.HttpResponse<O>>> buildExchangePublisher(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            Argument<O> bodyType,
            Argument<E> errorType) {
        AtomicReference<io.micronaut.http.HttpRequest> requestWrapper = new AtomicReference<>(request);
        return requestURI -> {
            Flowable<io.micronaut.http.HttpResponse<O>> responsePublisher = Flowable.create(emitter -> {

                boolean multipart = MediaType.MULTIPART_FORM_DATA_TYPE.equals(request.getContentType().orElse(null));
                if (poolMap != null && !multipart) {
                    try {
                        ChannelPool channelPool = poolMap.get(new RequestKey(requestURI));
                        Future<Channel> channelFuture = channelPool.acquire();
                        channelFuture.addListener(future -> {
                            if (future.isSuccess()) {
                                Channel channel = (Channel) future.get();
                                try {
                                    sendRequestThroughChannel(
                                            requestWrapper,
                                            bodyType,
                                            errorType,
                                            emitter,
                                            channel,
                                            channelPool
                                    );
                                } catch (Exception e) {
                                    emitter.onError(e);
                                }

                            } else {
                                Throwable cause = future.cause();
                                emitter.onError(
                                        new HttpClientException("Connect Error: " + cause.getMessage(), cause)
                                );
                            }
                        });
                    } catch (HttpClientException e) {
                        emitter.onError(e);
                    }
                } else {
                    SslContext sslContext = buildSslContext(requestURI);
                    ChannelFuture connectionFuture = doConnect(request, requestURI, sslContext, false, null);
                    connectionFuture.addListener(future -> {
                        if (!future.isSuccess()) {
                            Throwable cause = future.cause();
                            emitter.onError(
                                    new HttpClientException("Connect Error: " + cause.getMessage(), cause)
                            );
                        } else {
                            try {
                                sendRequestThroughChannel(
                                        requestWrapper,
                                        bodyType,
                                        errorType,
                                        emitter,
                                        connectionFuture.channel(),
                                        null);
                            } catch (Throwable e) {
                                emitter.onError(e);
                            }
                        }
                    });
                }

            }, BackpressureStrategy.ERROR);

            Publisher<io.micronaut.http.HttpResponse<O>> finalPublisher = applyFilterToResponsePublisher(
                    parentRequest,
                    request,
                    requestURI,
                    requestWrapper,
                    responsePublisher
            );
            Flowable<io.micronaut.http.HttpResponse<O>> finalFlowable;
            if (finalPublisher instanceof Flowable) {
                finalFlowable = (Flowable<io.micronaut.http.HttpResponse<O>>) finalPublisher;
            } else {
                finalFlowable = Flowable.fromPublisher(finalPublisher);
            }
            // apply timeout to flowable too in case a filter applied another policy
            Optional<Duration> readTimeout = configuration.getReadTimeout();
            if (readTimeout.isPresent()) {
                // add an additional second, because generally the timeout should occur
                // from the Netty request handling pipeline
                final Duration rt = readTimeout.get();
                if (!rt.isNegative()) {
                    Duration duration = rt.plus(Duration.ofSeconds(1));
                    finalFlowable = finalFlowable.timeout(
                            duration.toMillis(),
                            TimeUnit.MILLISECONDS
                    ).onErrorResumeNext(throwable -> {
                        if (throwable instanceof TimeoutException) {
                            return Flowable.error(ReadTimeoutException.TIMEOUT_EXCEPTION);
                        }
                        return Flowable.error(throwable);
                    });
                }
            }
            return finalFlowable;
        };
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
            return Publishers.just(requestURI);
        } else {
            if (loadBalancer == null) {
                return Publishers.just(new NoHostException("Request URI specifies no host to connect to"));
            }

            return Publishers.map(loadBalancer.select(getLoadBalancerDiscriminator()), server -> {
                        Optional<String> authInfo = server.getMetadata().get(io.micronaut.http.HttpHeaders.AUTHORIZATION_INFO, String.class);
                        if (request instanceof MutableHttpRequest && authInfo.isPresent()) {
                            ((MutableHttpRequest) request).getHeaders().auth(authInfo.get());
                        }
                        return server.resolve(includeContextPath ? prependContextPath(requestURI) : requestURI);
                    }
            );
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
                //should never happen
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

        RequestKey requestKey = new RequestKey(uri);
        return doConnect(request, requestKey.getHost(), requestKey.getPort(), sslCtx, isStream, contextConsumer);
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
        Bootstrap localBootstrap = this.bootstrap.clone();
        String acceptHeader = request.getHeaders().get(io.micronaut.http.HttpHeaders.ACCEPT);
        localBootstrap.handler(new HttpClientInitializer(
                sslCtx,
                host,
                port,
                isStream,
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
        if (io.micronaut.http.HttpRequest.SCHEME_HTTPS.equalsIgnoreCase(uriObject.getScheme())) {
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

    /**
     * @param parentRequest     The parent request
     * @param request           The request
     * @param requestURI        The URI of the request
     * @param requestWrapper    The request wrapper
     * @param responsePublisher The response publisher
     * @param <I>               The input type
     * @param <O>               The output type
     * @return The {@link Publisher} for the response
     */
    protected <I, O> Publisher<io.micronaut.http.HttpResponse<O>> applyFilterToResponsePublisher(
            io.micronaut.http.HttpRequest<?> parentRequest,
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            AtomicReference<io.micronaut.http.HttpRequest> requestWrapper,
            Publisher<io.micronaut.http.HttpResponse<O>> responsePublisher) {

        if (request instanceof MutableHttpRequest) {
            ((MutableHttpRequest<I>) request).uri(requestURI);

            List<HttpClientFilter> filters =
                    filterResolver.resolveFilters(request, clientFilterEntries);
            if (parentRequest != null) {
                filters.add(new ClientServerContextFilter(parentRequest));
            }

            OrderUtil.reverseSort(filters);
            Publisher<io.micronaut.http.HttpResponse<O>> finalResponsePublisher = responsePublisher;
            filters.add((req, chain) -> finalResponsePublisher);

            ClientFilterChain filterChain = buildChain(requestWrapper, filters);
            if (parentRequest != null) {
                responsePublisher = ServerRequestContext.with(parentRequest, (Supplier<Publisher<io.micronaut.http.HttpResponse<O>>>) () ->
                        (Publisher<io.micronaut.http.HttpResponse<O>>) filters.get(0).doFilter(request, filterChain));
            } else {
                responsePublisher = (Publisher<io.micronaut.http.HttpResponse<O>>) filters.get(0)
                        .doFilter(request, filterChain);
            }
        }

        return responsePublisher;
    }

    /**
     * @param request                The request
     * @param requestURI             The URI of the request
     * @param requestContentType     The request content type
     * @param permitsBody            Whether permits body
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
            io.reactivex.functions.Consumer<? super Throwable> onError,
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

                        Flowable<?> publisher = ConversionService.SHARED.convert(bodyValue, Flowable.class).orElseThrow(() ->
                                new IllegalArgumentException("Unconvertible reactive type: " + bodyValue)
                        );

                        Flowable<HttpContent> requestBodyPublisher = publisher.map(o -> {
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
                                ByteBuf encoded = registeredCodec.map(codec -> codec.encode(o, byteBufferFactory).asNativeBuffer())
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
                            requestBodyPublisher = requestBodyPublisher.map(new Function<HttpContent, HttpContent>() {
                                boolean first = true;

                                @Override
                                public HttpContent apply(HttpContent httpContent) {
                                    if (!first) {
                                        return HttpContentUtil.prefixComma(httpContent);
                                    } else {
                                        first = false;
                                        return httpContent;
                                    }
                                }
                            });
                            requestBodyPublisher = Flowable.concat(
                                    Flowable.fromCallable(HttpContentUtil::openBracket),
                                    requestBodyPublisher,
                                    Flowable.fromCallable(HttpContentUtil::closeBracket)
                            );
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
                        bodyContent = registeredCodec.map(codec -> codec.encode(bodyValue, byteBufferFactory).asNativeBuffer())
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

    private <I, O, E> void sendRequestThroughChannel(
            AtomicReference<io.micronaut.http.HttpRequest> requestWrapper,
            Argument<O> bodyType,
            Argument<E> errorType,
            FlowableEmitter<io.micronaut.http.HttpResponse<O>> emitter,
            Channel channel,
            ChannelPool channelPool) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        io.micronaut.http.HttpRequest<I> finalRequest = requestWrapper.get();
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
                emitter::tryOnError,
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
            log.debug("Sending HTTP Request: {} {}", nettyRequest.method(), nettyRequest.uri());
            log.debug("Chosen Server: {}({})", requestURI.getHost(), requestURI.getPort());
        }
        if (log.isTraceEnabled()) {
            traceRequest(finalRequest, nettyRequest);
        }

        addFullHttpResponseHandler(
                finalRequest,
                channel,
                channelPool,
                emitter,
                bodyType,
                errorType
        );
        requestWriter.writeAndClose(channel, channelPool, emitter);
    }

    private void streamRequestThroughChannel(
            io.micronaut.http.HttpRequest<?> parentRequest,
            URI requestURI,
            AtomicReference<io.micronaut.http.HttpRequest> requestWrapper,
            FlowableEmitter emitter,
            Channel channel,
            boolean failOnError) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        NettyRequestWriter requestWriter = prepareRequest(
                requestWrapper.get(),
                requestURI,
                emitter,
                false
        );
        HttpRequest nettyRequest = requestWriter.getNettyRequest();
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE_STREAM, new SimpleChannelInboundHandler<StreamedHttpResponse>() {

            AtomicBoolean received = new AtomicBoolean(false);

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                if (received.compareAndSet(false, true)) {
                    emitter.onError(cause);
                }
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, StreamedHttpResponse msg) {
                if (received.compareAndSet(false, true)) {
                    HttpResponseStatus status = msg.status();
                    int statusCode = status.code();
                    HttpStatus httpStatus;
                    try {
                        httpStatus = HttpStatus.valueOf(statusCode);
                    } catch (IllegalArgumentException e) {
                        emitter.onError(e);
                        return;
                    }

                    NettyStreamedHttpResponse response = new NettyStreamedHttpResponse(msg, httpStatus);
                    HttpHeaders headers = msg.headers();
                    if (log.isTraceEnabled()) {
                        log.trace("HTTP Client Streaming Response Received ({}) for Request: {} {}", msg.status(), nettyRequest.method().name(), nettyRequest.uri());
                        traceHeaders(headers);
                    }

                    if (statusCode > 300 && statusCode < 400 && configuration.isFollowRedirects() && headers.contains(HttpHeaderNames.LOCATION)) {
                        String location = headers.get(HttpHeaderNames.LOCATION);
                        Flowable<io.micronaut.http.HttpResponse<Object>> redirectedExchange;
                        try {
                            MutableHttpRequest<Object> redirectRequest = io.micronaut.http.HttpRequest.GET(location);
                            setRedirectHeaders(nettyRequest, redirectRequest);
                            redirectedExchange = Flowable.fromPublisher(resolveRequestURI(redirectRequest, false))
                                    .flatMap(uri -> buildStreamExchange(parentRequest, redirectRequest, uri));

                            //noinspection SubscriberImplementation
                            redirectedExchange.subscribe(new Subscriber<io.micronaut.http.HttpResponse<Object>>() {
                                Subscription sub;

                                @Override
                                public void onSubscribe(Subscription s) {
                                    s.request(1);
                                    this.sub = s;
                                }

                                @Override
                                public void onNext(io.micronaut.http.HttpResponse<Object> objectHttpResponse) {
                                    emitter.onNext(objectHttpResponse);
                                    sub.cancel();
                                }

                                @Override
                                public void onError(Throwable t) {
                                    emitter.onError(t);
                                    sub.cancel();
                                }

                                @Override
                                public void onComplete() {
                                    emitter.onComplete();
                                }
                            });
                        } catch (Exception e) {
                            emitter.onError(e);
                        }
                    } else {
                        boolean errorStatus = statusCode >= 400;
                        if (errorStatus && failOnError) {
                            emitter.onError(new HttpClientResponseException(response.getStatus().getReason(), response));
                        } else {
                            emitter.onNext(response);
                            emitter.onComplete();
                        }

                    }

                }
            }
        });
        if (log.isDebugEnabled()) {
            log.debug("Sending HTTP Request: {} {}", nettyRequest.method(), nettyRequest.uri());
            log.debug("Chosen Server: {}({})", requestURI.getHost(), requestURI.getPort());
        }
        if (log.isTraceEnabled()) {
            traceRequest(requestWrapper.get(), nettyRequest);
        }

        requestWriter.writeAndClose(channel, null, emitter);
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

    @SuppressWarnings("MagicNumber")
    private <O, E> void addFullHttpResponseHandler(
            io.micronaut.http.HttpRequest<?> request,
            Channel channel,
            ChannelPool channelPool,
            FlowableEmitter<io.micronaut.http.HttpResponse<O>> emitter,
            Argument<O> bodyType, Argument<E> errorType) {
        ChannelPipeline pipeline = channel.pipeline();
        final SimpleChannelInboundHandler<FullHttpResponse> newHandler = new SimpleChannelInboundHandler<FullHttpResponse>(false) {

            AtomicBoolean complete = new AtomicBoolean(false);
            boolean keepAlive = true;

            @Override
            protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpResponse fullResponse) {
                try {

                    HttpResponseStatus status = fullResponse.status();
                    int statusCode = status.code();
                    HttpStatus httpStatus;
                    try {
                        httpStatus = HttpStatus.valueOf(statusCode);
                    } catch (IllegalArgumentException e) {
                        if (complete.compareAndSet(false, true)) {
                            emitter.tryOnError(e);
                        } else if (LOG.isWarnEnabled()) {
                            LOG.warn("Unsupported http status after handler completed: " + e.getMessage(), e);
                        }
                        return;
                    }

                    try {
                        HttpHeaders headers = fullResponse.headers();
                        if (log.isTraceEnabled()) {
                            log.trace("HTTP Client Response Received for Request: {} {}", request.getMethod(), request.getUri());
                            log.trace("Status Code: {}", status);
                            traceHeaders(headers);
                            traceBody("Response", fullResponse.content());
                        }

                        // it is a redirect
                        if (statusCode > 300 && statusCode < 400 && configuration.isFollowRedirects() && headers.contains(HttpHeaderNames.LOCATION)) {
                            String location = headers.get(HttpHeaderNames.LOCATION);
                            final MutableHttpRequest<Object> redirectRequest = io.micronaut.http.HttpRequest.GET(location);
                            setRedirectHeaders(request, redirectRequest);
                            Flowable<io.micronaut.http.HttpResponse<O>> redirectExchange = redirectExchange(redirectRequest, bodyType, DEFAULT_ERROR_TYPE);
                            redirectExchange.first(io.micronaut.http.HttpResponse.notFound())
                                    .subscribe((oHttpResponse, throwable) -> {
                                        if (throwable != null) {
                                            emitter.tryOnError(throwable);

                                        } else {
                                            emitter.onNext(oHttpResponse);
                                            emitter.onComplete();
                                        }
                                    });
                            return;
                        }
                        if (statusCode == HttpStatus.NO_CONTENT.getCode()) {
                            // normalize the NO_CONTENT header, since http content aggregator adds it even if not present in the response
                            headers.remove(HttpHeaderNames.CONTENT_LENGTH);
                        }

                        boolean convertBodyWithBodyType = statusCode < 400 ||
                                (!DefaultHttpClient.this.configuration.isExceptionOnErrorStatus() && bodyType.equalsType(errorType));
                        FullNettyClientHttpResponse<O> response
                                = new FullNettyClientHttpResponse<>(fullResponse, httpStatus, mediaTypeCodecRegistry, byteBufferFactory, bodyType, convertBodyWithBodyType);

                        if (complete.compareAndSet(false, true)) {
                            if (convertBodyWithBodyType) {
                                emitter.onNext(response);
                                response.onComplete();
                                emitter.onComplete();
                            } else { // error flow
                                try {
                                    HttpClientResponseException clientError;
                                    if (errorType != HttpClient.DEFAULT_ERROR_TYPE) {
                                        clientError = new HttpClientResponseException(
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
                                        clientError = new HttpClientResponseException(
                                                status.reasonPhrase(),
                                                response
                                        );
                                    }
                                    try {
                                        emitter.tryOnError(clientError);
                                    } finally {
                                        response.onComplete();
                                    }
                                } catch (Throwable t) {
                                    if (t instanceof HttpClientResponseException) {
                                        try {
                                            emitter.tryOnError(t);
                                        } finally {
                                            response.onComplete();
                                        }
                                    } else {
                                        response.onComplete();
                                        FullNettyClientHttpResponse<Object> errorResponse = new FullNettyClientHttpResponse<>(
                                                fullResponse,
                                                httpStatus,
                                                mediaTypeCodecRegistry,
                                                byteBufferFactory,
                                                null,
                                                false
                                        );
                                        errorResponse.onComplete();
                                        HttpClientResponseException clientResponseError = new HttpClientResponseException(
                                                "Error decoding HTTP error response body: " + t.getMessage(),
                                                t,
                                                errorResponse,
                                                null
                                        );
                                        emitter.tryOnError(clientResponseError);
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        if (complete.compareAndSet(false, true)) {
                            if (t instanceof HttpClientResponseException) {
                                emitter.tryOnError(t);
                            } else {
                                FullNettyClientHttpResponse<Object> response = new FullNettyClientHttpResponse<>(fullResponse, httpStatus, mediaTypeCodecRegistry, byteBufferFactory, null, false);
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
                                    emitter.tryOnError(clientResponseError);
                                } finally {
                                    response.onComplete();
                                }
                            }
                        } else {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("Exception fired after handler completed: " + t.getMessage(), t);
                            }
                        }
                    }
                } finally {
                    if (fullResponse.refCnt() > 0) {
                        try {
                            ReferenceCountUtil.release(fullResponse);
                        } catch (Throwable e) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Failed to release response: {}", fullResponse);
                            }
                        }
                    }
                    if (!HttpUtil.isKeepAlive(fullResponse)) {
                        keepAlive = false;
                    }
                    pipeline.remove(this);

                }
            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) {
                if (channelPool != null) {
                    if (readTimeoutMillis != null) {
                        ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT);
                    }
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
                if (readTimeoutMillis != null) {

                    if (httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {
                        Http2SettingsHandler settingsHandler = (Http2SettingsHandler) ctx.pipeline().get(HANDLER_HTTP2_SETTINGS);
                        if (settingsHandler != null) {
                            settingsHandler.promise.addListener(future -> {
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

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                try {
                    if (complete.compareAndSet(false, true)) {

                        String message = cause.getMessage();
                        if (message == null) {
                            message = cause.getClass().getSimpleName();
                        }
                        if (log.isTraceEnabled()) {
                            log.trace("HTTP Client exception ({}) occurred for request : {} {}",
                                    message, request.getMethodName(), request.getUri());
                        }

                        if (cause instanceof TooLongFrameException) {
                            emitter.tryOnError(new ContentLengthExceededException(configuration.getMaxContentLength()));
                        } else if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
                            emitter.tryOnError(ReadTimeoutException.TIMEOUT_EXCEPTION);
                        } else {
                            emitter.tryOnError(new HttpClientException("Error occurred reading HTTP response: " + message, cause));
                        }
                    }
                } finally {
                    keepAlive = false;
                    pipeline.remove(this);
                }
            }
        };
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_FULL_HTTP_RESPONSE, newHandler);
    }

    private void setRedirectHeaders(@Nullable HttpRequest request, MutableHttpRequest<Object> redirectRequest) {
        if (request != null) {
            request.headers().forEach(header -> redirectRequest.header(header.getKey(), header.getValue()));
        }
    }

    private void setRedirectHeaders(@Nullable io.micronaut.http.HttpRequest<?> request, MutableHttpRequest<Object> redirectRequest) {
        if (request != null) {
            final Iterator<Map.Entry<String, List<String>>> headerIterator = request.getHeaders().iterator();
            while (headerIterator.hasNext()) {
                final Map.Entry<String, List<String>> originalHeader = headerIterator.next();
                final List<String> originalHeaderValue = originalHeader.getValue();
                if (originalHeaderValue != null && !originalHeaderValue.isEmpty()) {
                    final Iterator<String> headerValueIterator = originalHeaderValue.iterator();
                    while (headerValueIterator.hasNext()) {
                        final String value = headerValueIterator.next();
                        if (value != null) {
                            redirectRequest.header(originalHeader.getKey(), value);
                        }
                    }
                }
            }
        }
    }

    private ClientFilterChain buildChain(AtomicReference<io.micronaut.http.HttpRequest> requestWrapper, List<HttpClientFilter> filters) {
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
                return httpFilter.doFilter(requestWrapper.getAndSet(request), this);
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
        ObjectMapper objectMapper = new ObjectMapperFactory().objectMapper(null, null);
        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        return MediaTypeCodecRegistry.of(
                new JsonMediaTypeCodec(objectMapper, applicationConfiguration, null), new JsonStreamMediaTypeCodec(objectMapper, applicationConfiguration, null)
        );
    }

    private <I> NettyRequestWriter prepareRequest(
            io.micronaut.http.HttpRequest<I> request,
            URI requestURI,
            FlowableEmitter<HttpResponse<Object>> emitter,
            boolean closeChannelAfterWrite) throws HttpPostRequestEncoder.ErrorDataEncoderException {
        MediaType requestContentType = request
                .getContentType()
                .orElse(MediaType.APPLICATION_JSON_TYPE);

        boolean permitsBody = io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod());
        MutableHttpRequest clientHttpRequest = (MutableHttpRequest) request;
        NettyRequestWriter requestWriter = buildNettyRequest(
                clientHttpRequest,
                requestURI,
                requestContentType,
                permitsBody,
                emitter::tryOnError,
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
                if (connectionTimeAliveMillis != null) {
                    boolean shouldCloseOnRelease = Boolean.TRUE.equals(ch.attr(ConnectTTLHandler.RELEASE_CHANNEL).get());

                    if (shouldCloseOnRelease && ch.isOpen() && !ch.eventLoop().isShuttingDown()) {
                        ch.close();
                    }
                }

                if (readTimeoutMillis != null) {
                    ChannelPipeline pipeline = ch.pipeline();
                    if (pipeline.context(ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT) != null) {
                        pipeline.remove(ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT);
                    }
                }
            }
        };
    }

    @Override
    public boolean isClientChannel() {
        return true;
    }

    @Override
    public void doOnConnect(@NonNull ChannelPipelineListener listener) {
        this.pipelineListeners.add(Objects.requireNonNull(listener, "The listener cannot be null"));
    }

    @Override
    public Flowable<MutableHttpResponse<?>> proxy(io.micronaut.http.HttpRequest<?> request) {
        return Flowable.fromPublisher(resolveRequestURI(request))
                .flatMap(requestURI -> {
                    AtomicReference<io.micronaut.http.HttpRequest> requestWrapper = new AtomicReference<>(request);
                    Flowable<MutableHttpResponse<Object>> proxyResponsePublisher = Flowable.create(emitter -> {
                        SslContext sslContext = buildSslContext(requestURI);
                        ChannelFuture channelFuture;
                        try {
                            if (httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {

                                channelFuture = doConnect(request, requestURI, sslContext, true, channelHandlerContext -> {
                                    try {
                                        final Channel channel = channelHandlerContext.channel();
                                        request.setAttribute(NettyClientHttpRequest.CHANNEL, channel);
                                        streamRequestThroughChannel(
                                                request,
                                                requestURI,
                                                requestWrapper,
                                                emitter,
                                                channel,
                                                false
                                        );
                                    } catch (Throwable e) {
                                        emitter.onError(e);
                                    }
                                });
                            } else {
                                channelFuture = doConnect(request, requestURI, sslContext, true, null);
                                channelFuture
                                        .addListener((ChannelFutureListener) f -> {
                                            if (f.isSuccess()) {
                                                Channel channel = f.channel();
                                                request.setAttribute(NettyClientHttpRequest.CHANNEL, channel);
                                                streamRequestThroughChannel(
                                                        request,
                                                        requestURI,
                                                        requestWrapper,
                                                        emitter,
                                                        channel,
                                                        false
                                                );
                                            } else {
                                                Throwable cause = f.cause();
                                                emitter.onError(
                                                        new HttpClientException("Connect error:" + cause.getMessage(), cause)
                                                );
                                            }
                                        });
                            }
                        } catch (HttpClientException e) {
                            emitter.onError(e);
                            return;
                        }

                        Disposable disposable = buildDisposableChannel(channelFuture);
                        emitter.setDisposable(disposable);
                        emitter.setCancellable(disposable::dispose);
                    }, BackpressureStrategy.BUFFER);
                    // apply filters
                    //noinspection unchecked
                    proxyResponsePublisher = Flowable.fromPublisher(
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


    /**
     * Initializes the HTTP client channel.
     */
    protected class HttpClientInitializer extends ChannelInitializer<SocketChannel> {

        final SslContext sslContext;
        final String host;
        final int port;
        final boolean stream;
        final boolean acceptsEvents;
        Http2SettingsHandler settingsHandler;
        private final Consumer<ChannelHandlerContext> contextConsumer;

        /**
         * @param sslContext      The ssl context
         * @param host            The host
         * @param port            The port
         * @param stream          Whether is stream
         * @param acceptsEvents   Whether an event stream is accepted
         * @param contextConsumer The context consumer
         */
        protected HttpClientInitializer(
                SslContext sslContext,
                String host,
                int port,
                boolean stream,
                boolean acceptsEvents,
                Consumer<ChannelHandlerContext> contextConsumer) {
            this.sslContext = sslContext;
            this.stream = stream;
            this.host = host;
            this.port = port;
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
                    SslHandler sslHandler = sslContext.newHandler(
                            ch.alloc(),
                            host,
                            port
                    );
                    p.addLast(ChannelPipelineCustomizer.HANDLER_SSL, sslHandler);
                }

                // Pool connections require alternative timeout handling
                if (poolMap == null) {
                    // read timeout settings are not applied to streamed requests.
                    // instead idle timeout settings are applied.
                    if (stream && readTimeoutMillis == null) {
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
            // to delimit the content by lines
            if (acceptsEventStream()) {
                p.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_EVENT_STREAM, new LineBasedFrameDecoder(configuration.getMaxContentLength(), true, true) {

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof HttpContent) {
                            if (msg instanceof LastHttpContent) {
                                super.channelRead(ctx, msg);
                            } else {
                                super.channelRead(ctx, ((HttpContent) msg).content());
                            }
                        } else {
                            super.channelRead(ctx, msg);
                        }
                    }
                });

                p.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_CONTENT, new SimpleChannelInboundHandler<ByteBuf>(false) {

                    @Override
                    public boolean acceptInboundMessage(Object msg) {
                        return msg instanceof ByteBuf;
                    }

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                        try {
                            ctx.fireChannelRead(new DefaultHttpContent(msg.copy()));
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
                    } else {
                        super.userEventTriggered(ctx, evt);
                    }
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
    private static final class Http2SettingsHandler extends SimpleChannelInboundHandler<Http2Settings> {
        private final ChannelPromise promise;

        /**
         * Create new instance.
         *
         * @param promise Promise object used to notify when first settings are received
         */
        Http2SettingsHandler(ChannelPromise promise) {
            this.promise = promise;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2Settings msg) {
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
            this.secure = io.micronaut.http.HttpRequest.SCHEME_HTTPS.equalsIgnoreCase(requestURI.getScheme());
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
        protected void writeAndClose(Channel channel, ChannelPool channelPool, FlowableEmitter<?> emitter) {
            final ChannelPipeline pipeline = channel.pipeline();
            if (httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {
                final boolean isSecure = sslContext != null &&
                        io.micronaut.http.HttpRequest.SCHEME_HTTPS.equalsIgnoreCase(scheme);
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
                    settingsHandler.promise.addListener(future -> {
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

        private void processRequestWrite(Channel channel, ChannelPool channelPool, FlowableEmitter<?> emitter, ChannelPipeline pipeline) {
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
                FlowableEmitter<?> emitter,
                ChannelFuture channelFuture,
                boolean closeChannelAfterWrite) {
            channelFuture.addListener(f -> {
                try {
                    if (!f.isSuccess()) {
                        if (!emitter.isCancelled()) {
                            emitter.onError(f.cause());
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
}
