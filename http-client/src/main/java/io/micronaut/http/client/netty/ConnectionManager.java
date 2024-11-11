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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientExceptionUtils;
import io.micronaut.http.client.netty.ssl.ClientSslBuilder;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexActiveStreamsException;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.pcap.PcapWriteHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler;
import io.netty.incubator.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInitializer;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.DefaultNameResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.resolver.RoundRobinInetAddressResolver;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Connection manager for {@link DefaultHttpClient}. This class manages the lifecycle of netty
 * channels (wrapped in {@link PoolHandle}s), including pooling and timeouts.<br>
 * Note: This class is public for use in micronaut-oracle-cloud.
 */
@Internal
public class ConnectionManager {

    final NettyClientCustomizer clientCustomizer;

    private final HttpVersionSelection httpVersion;
    private final Logger log;
    private final Map<DefaultHttpClient.RequestKey, Pool> pools = new ConcurrentHashMap<>();
    private final ClientSslBuilder nettyClientSslBuilder;

    private EventLoopGroup group;
    private final boolean shutdownGroup;

    private final AddressResolverGroup<?> resolverGroup;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ThreadFactory threadFactory;
    private final ChannelFactory<? extends Channel> socketChannelFactory;
    private final ChannelFactory<? extends Channel> udpChannelFactory;
    private Bootstrap bootstrap;
    private Bootstrap udpBootstrap;
    private final HttpClientConfiguration configuration;
    private volatile SslContext sslContext;
    private volatile /* QuicSslContext */ Object http3SslContext;
    private volatile SslContext websocketSslContext;
    private final String informationalServiceId;

    /**
     * Copy constructor used by the test suite to patch this manager.
     *
     * @param from Original connection manager
     */
    ConnectionManager(ConnectionManager from) {
        this.httpVersion = from.httpVersion;
        this.log = from.log;
        this.group = from.group;
        this.shutdownGroup = from.shutdownGroup;
        this.resolverGroup = from.resolverGroup;
        this.threadFactory = from.threadFactory;
        this.socketChannelFactory = from.socketChannelFactory;
        this.udpChannelFactory = from.udpChannelFactory;
        this.bootstrap = from.bootstrap;
        this.udpBootstrap = from.udpBootstrap;
        this.configuration = from.configuration;
        this.sslContext = from.sslContext;
        this.http3SslContext = from.http3SslContext;
        this.websocketSslContext = from.websocketSslContext;
        this.clientCustomizer = from.clientCustomizer;
        this.informationalServiceId = from.informationalServiceId;
        this.nettyClientSslBuilder = from.nettyClientSslBuilder;
        this.running.set(from.running.get());
    }

    ConnectionManager(
        Logger log,
        @Nullable EventLoopGroup eventLoopGroup,
        @Nullable ThreadFactory threadFactory,
        HttpClientConfiguration configuration,
        @Nullable HttpVersionSelection httpVersion,
        ChannelFactory<? extends Channel> socketChannelFactory,
        ChannelFactory<? extends Channel> udpChannelFactory,
        ClientSslBuilder nettyClientSslBuilder,
        NettyClientCustomizer clientCustomizer,
        String informationalServiceId,
        @Nullable AddressResolverGroup<?> resolverGroup) {

        if (httpVersion == null) {
            httpVersion = HttpVersionSelection.forClientConfiguration(configuration);
        }

        this.log = log;
        this.httpVersion = httpVersion;
        this.threadFactory = threadFactory;
        this.socketChannelFactory = socketChannelFactory;
        this.udpChannelFactory = udpChannelFactory;
        this.configuration = configuration;
        this.clientCustomizer = clientCustomizer;
        this.informationalServiceId = informationalServiceId;
        this.nettyClientSslBuilder = nettyClientSslBuilder;

        if (eventLoopGroup != null) {
            group = eventLoopGroup;
            shutdownGroup = false;
        } else {
            group = createEventLoopGroup(configuration, threadFactory);
            shutdownGroup = true;
        }

        this.resolverGroup = resolverGroup == null ? getResolver(configuration.getDnsResolutionMode()) : resolverGroup;

        refresh();
    }

    final void refresh() {
        SslContext oldSslContext = sslContext;
        SslContext oldWebsocketSslContext = websocketSslContext;
        websocketSslContext = null;
        if (configuration.getSslConfiguration().isEnabled()) {
            sslContext = nettyClientSslBuilder.build(configuration.getSslConfiguration(), httpVersion);
        } else {
            sslContext = null;
        }
        if (httpVersion.isHttp3()) {
            http3SslContext = nettyClientSslBuilder.buildHttp3(configuration.getSslConfiguration());
        } else {
            http3SslContext = null;
        }
        initBootstrap();
        running.set(true);
        for (Pool pool : pools.values()) {
            pool.forEachConnection(c -> ((Pool.ConnectionHolder) c).windDownConnection());
        }
        ReferenceCountUtil.release(oldSslContext);
        ReferenceCountUtil.release(oldWebsocketSslContext);
    }

    /**
     * Creates the {@link NioEventLoopGroup} for this client.
     *
     * @param configuration The configuration
     * @param threadFactory The thread factory
     * @return The group
     */
    private static NioEventLoopGroup createEventLoopGroup(HttpClientConfiguration configuration, ThreadFactory threadFactory) {
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
                group = new NioEventLoopGroup(NettyThreadFactory.getDefaultEventLoopThreads(), threadFactory);
            } else {

                group = new NioEventLoopGroup();
            }
        }
        return group;
    }

    /**
     * Allocator for this connection manager. Used by micronaut-oracle-cloud.
     *
     * @return The configured allocator
     */
    public final ByteBufAllocator alloc() {
        return (ByteBufAllocator) bootstrap.config().options().getOrDefault(ChannelOption.ALLOCATOR, ByteBufAllocator.DEFAULT);
    }

    EventLoopGroup getGroup() {
        return group;
    }

    /**
     * For testing.
     *
     * @return Connected channels in all pools
     * @since 4.0.0
     */
    @NonNull
    @SuppressWarnings("unused")
    final List<Channel> getChannels() {
        List<Channel> channels = new ArrayList<>();
        for (Pool pool : pools.values()) {
            pool.forEachConnection(c -> channels.add(((Pool.ConnectionHolder) c).channel));
        }
        return channels;
    }

    /**
     * For testing.
     *
     * @return Number of running requests
     * @since 4.0.0
     */
    @SuppressWarnings("unused")
    final int liveRequestCount() {
        AtomicInteger count = new AtomicInteger();
        for (Pool pool : pools.values()) {
            pool.forEachConnection(c -> {
                if (c instanceof Pool.Http1ConnectionHolder holder) {
                    if (holder.hasLiveRequests()) {
                        count.incrementAndGet();
                    }
                } else {
                    count.addAndGet(((Pool.Http2ConnectionHolder) c).liveRequests.get());
                }
            });
        }
        return count.get();
    }

    /**
     * @see DefaultHttpClient#start()
     */
    public final void start() {
        if (running.compareAndSet(false, true)) {
            // only need to start new group if it's managed by us
            if (shutdownGroup) {
                group = createEventLoopGroup(configuration, threadFactory);
                initBootstrap(); // rebuild bootstrap with new group
            }
        }
    }

    private void initBootstrap() {
        this.bootstrap = new Bootstrap()
            .group(group)
            .channelFactory(socketChannelFactory)
            .option(ChannelOption.SO_KEEPALIVE, true);
        if (httpVersion.isHttp3()) {
            this.udpBootstrap = new Bootstrap()
                .group(group)
                .channelFactory(udpChannelFactory);
        }

        Optional<Duration> connectTimeout = configuration.getConnectTimeout();
        connectTimeout.ifPresent(duration -> bootstrap.option(
            ChannelOption.CONNECT_TIMEOUT_MILLIS,
            (int) duration.toMillis()
        ));

        for (Map.Entry<String, Object> entry : configuration.getChannelOptions().entrySet()) {
            Object v = entry.getValue();
            if (v != null) {
                String channelOption = entry.getKey();
                bootstrap.option(ChannelOption.valueOf(NameUtils.underscoreSeparate(channelOption).toUpperCase(Locale.ENGLISH)), v);
            }
        }

        bootstrap.resolver(resolverGroup);
    }

    static @NonNull AddressResolverGroup<? extends SocketAddress> getResolver(HttpClientConfiguration.@NonNull DnsResolutionMode mode) {
        return switch (mode) {
            case DEFAULT -> DefaultAddressResolverGroup.INSTANCE;
            case NOOP -> NoopAddressResolverGroup.INSTANCE;
            case ROUND_ROBIN -> new AddressResolverGroup<InetSocketAddress>() {
                @Override
                protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
                    return new InetSocketAddressResolver(executor, new RoundRobinInetAddressResolver(executor, new DefaultNameResolver(executor)));
                }
            };
        };
    }

    /**
     * @see DefaultHttpClient#stop()
     */
    public final void shutdown() {
        if (running.compareAndSet(true, false)) {

            for (Pool pool : pools.values()) {
                pool.shutdown();
            }
            if (shutdownGroup) {
                Duration shutdownTimeout = configuration.getShutdownTimeout()
                    .orElse(Duration.ofMillis(HttpClientConfiguration.DEFAULT_SHUTDOWN_TIMEOUT_MILLISECONDS));
                Duration shutdownQuietPeriod = configuration.getShutdownQuietPeriod()
                    .orElse(Duration.ofMillis(HttpClientConfiguration.DEFAULT_SHUTDOWN_QUIET_PERIOD_MILLISECONDS));

                Future<?> future = group.shutdownGracefully(
                    shutdownQuietPeriod.toMillis(),
                    shutdownTimeout.toMillis(),
                    TimeUnit.MILLISECONDS
                );
                try {
                    future.await(shutdownTimeout.toMillis());
                } catch (InterruptedException e) {
                    // ignore
                    Thread.currentThread().interrupt();
                }
            }
            ReferenceCountUtil.release(sslContext);
            ReferenceCountUtil.release(websocketSslContext);
            resolverGroup.close();
            sslContext = null;
            websocketSslContext = null;
        }
    }

    /**
     * @see DefaultHttpClient#isRunning()
     *
     * @return Whether this connection manager is still running and can serve requests
     */
    public final boolean isRunning() {
        return running.get() && !group.isShutdown();
    }

    /**
     * Use the bootstrap to connect to the given host. Also does some proxy setup. This method is
     * not final: The test suite overrides it to return embedded channels instead.
     *
     * @param requestKey The host to connect to
     * @param channelInitializer The initializer to use
     * @return Future that terminates when the TCP connection is established.
     */
    ChannelFuture doConnect(DefaultHttpClient.RequestKey requestKey, CustomizerAwareInitializer channelInitializer) {
        String host = requestKey.getHost();
        int port = requestKey.getPort();
        Bootstrap localBootstrap = bootstrap.clone();
        Proxy proxy = configuration.resolveProxy(requestKey.isSecure(), host, port);
        if (proxy.type() != Proxy.Type.DIRECT) {
            localBootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
        }
        localBootstrap.handler(channelInitializer)
            .remoteAddress(host, port);
        channelInitializer.bootstrappedCustomizer = clientCustomizer.specializeForBootstrap(localBootstrap);
        return localBootstrap.connect();
    }

    /**
     * Builds an {@link SslContext} for the given URI if necessary.
     *
     * @return The {@link SslContext} instance
     */
    @Nullable
    private SslContext buildSslContext(DefaultHttpClient.RequestKey requestKey) {
        final SslContext sslCtx;
        if (requestKey.isSecure()) {
            sslCtx = sslContext;
            //Allow https requests to be sent if SSL is disabled but a proxy is present
            if (sslCtx == null && !configuration.getProxyAddress().isPresent()) {
                throw decorate(new HttpClientException("Cannot send HTTPS request. SSL is disabled"));
            }
        } else {
            sslCtx = null;
        }
        return sslCtx;
    }

    /**
     * Get a connection for non-websocket http client methods.
     *
     * @param requestKey The remote to connect to
     * @param blockHint  Optional information about what threads are blocked for this connection request
     * @return A mono that will complete once the channel is ready for transmission
     */
    public final ExecutionFlow<PoolHandle> connect(DefaultHttpClient.RequestKey requestKey, @Nullable BlockHint blockHint) {
        return pools.computeIfAbsent(requestKey, Pool::new).acquire(blockHint);
    }

    /**
     * Builds an {@link SslContext} for the given WebSocket URI if necessary.
     *
     * @return The {@link SslContext} instance
     */
    @Nullable
    private SslContext buildWebsocketSslContext(DefaultHttpClient.RequestKey requestKey) {
        SslContext sslCtx = websocketSslContext;
        if (requestKey.isSecure()) {
            if (configuration.getSslConfiguration().isEnabled()) {
                if (sslCtx == null) {
                    synchronized (this) {
                        sslCtx = websocketSslContext;
                        if (sslCtx == null) {
                            sslCtx = nettyClientSslBuilder.build(configuration.getSslConfiguration(), HttpVersionSelection.forWebsocket());
                            websocketSslContext = sslCtx;
                        }
                    }
                }
            } else if (configuration.getProxyAddress().isEmpty()) {
                throw decorate(new HttpClientException("Cannot send WSS request. SSL is disabled"));
            }
        }
        return sslCtx;
    }

    /**
     * Connect to a remote websocket. The given {@link ChannelHandler} is added to the pipeline
     * when the handshakes complete.
     *
     * @param requestKey The remote to connect to
     * @param handler The websocket message handler
     * @return A mono that will complete when the handshakes complete
     */
    final Mono<?> connectForWebsocket(DefaultHttpClient.RequestKey requestKey, ChannelHandler handler) {
        Sinks.Empty<Object> initial = new CancellableMonoSink<>(null);

        ChannelFuture connectFuture = doConnect(requestKey, new CustomizerAwareInitializer() {
            @Override
            protected void initChannel(@NonNull Channel ch) {
                addLogHandler(ch);

                SslContext sslContext = buildWebsocketSslContext(requestKey);
                if (sslContext != null) {
                    ch.pipeline().addLast(configureSslHandler(sslContext.newHandler(ch.alloc(), requestKey.getHost(), requestKey.getPort())));
                }

                ch.pipeline()
                    .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC, new HttpClientCodec())
                    .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_AGGREGATOR, new HttpObjectAggregator(configuration.getMaxContentLength()));

                Optional<Duration> readIdleTime = configuration.getReadIdleTimeout();
                if (readIdleTime.isPresent()) {
                    Duration duration = readIdleTime.get();
                    if (!duration.isNegative()) {
                        ch.pipeline()
                            .addLast(ChannelPipelineCustomizer.HANDLER_IDLE_STATE, new IdleStateHandler(duration.toMillis(), duration.toMillis(), duration.toMillis(), TimeUnit.MILLISECONDS));
                    }
                }

                try {
                    if (configuration.getWebSocketCompressionConfiguration() != null && configuration.getWebSocketCompressionConfiguration().isEnabled()) {
                        ch.pipeline().addLast(WebSocketClientCompressionHandler.INSTANCE);
                    }
                    ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_WEBSOCKET_CLIENT, handler);
                    bootstrappedCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION).onInitialPipelineBuilt();
                    if (initial.tryEmitEmpty().isSuccess()) {
                        return;
                    }
                } catch (Throwable e) {
                    initial.tryEmitError(new WebSocketSessionException("Error opening WebSocket client session: " + e.getMessage(), e));
                }
                // failed
                ch.close();
            }
        });
        withPropagation(connectFuture, future -> {
            if (!future.isSuccess()) {
                initial.tryEmitError(future.cause());
            }
        });

        return initial.asMono();
    }

    private void configureProxy(ChannelPipeline pipeline, boolean secure, String host, int port) {
        Proxy proxy = configuration.resolveProxy(secure, host, port);
        if (Proxy.NO_PROXY.equals(proxy)) {
            return;
        }
        Proxy.Type proxyType = proxy.type();
        SocketAddress proxyAddress = proxy.address();
        String username = configuration.getProxyUsername().orElse(null);
        String password = configuration.getProxyPassword().orElse(null);

        if (proxyAddress instanceof InetSocketAddress isa) {
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

    final <V, C extends Future<V>> void withPropagation(Future<? extends V> channelFuture, GenericFutureListener<C> listener) {
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        channelFuture.addListener(f -> {
            try (PropagatedContext.Scope ignored = propagatedContext.propagate()) {
                //noinspection unchecked
                listener.operationComplete((C) f);
            }
        });
    }

    private Http2FrameCodec makeFrameCodec() {
        Http2FrameCodecBuilder builder = Http2FrameCodecBuilder.forClient();
        configuration.getLogLevel().ifPresent(logLevel -> {
            try {
                final LogLevel nettyLevel =
                    LogLevel.valueOf(logLevel.name());
                builder.frameLogger(new Http2FrameLogger(nettyLevel, DefaultHttpClient.class));
            } catch (IllegalArgumentException e) {
                throw decorate(new HttpClientException("Unsupported log level: " + logLevel));
            }
        });
        return builder.build();
    }

    private SslHandler configureSslHandler(SslHandler sslHandler) {
        sslHandler.setHandshakeTimeoutMillis(configuration.getSslConfiguration().getHandshakeTimeout().toMillis());
        SSLEngine engine = sslHandler.engine();
        SSLParameters params = engine.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(params);
        return sslHandler;
    }

    /**
     * Initializer for HTTP1.1, called either in plaintext mode, or after ALPN in TLS.
     *
     * @param ch The plaintext channel
     */
    private void initHttp1(Channel ch) {
        addLogHandler(ch);

        ch.pipeline()
            .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC, new HttpClientCodec())
            .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_DECODER, new HttpContentDecompressor());
    }

    private void addLogHandler(Channel ch) {
        configuration.getLogLevel().ifPresent(logLevel -> {
            try {
                final LogLevel nettyLevel =
                    LogLevel.valueOf(logLevel.name());
                ch.pipeline().addLast(new LoggingHandler(DefaultHttpClient.class, nettyLevel));
            } catch (IllegalArgumentException e) {
                throw decorate(new HttpClientException("Unsupported log level: " + logLevel));
            }
        });
    }

    private void insertPcapLoggingHandlerLazy(Channel ch, String qualifier) {
        if (configuration.getPcapLoggingPathPattern() == null) {
            return;
        }

        if (ch.isActive()) {
            ChannelHandler actual = createPcapLoggingHandler(ch, qualifier);
            ch.pipeline().addLast("pcap-" + qualifier, actual);
        } else {
            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    ChannelHandler actual = createPcapLoggingHandler(ch, qualifier);
                    ctx.pipeline().addBefore(ctx.name(), "pcap-" + qualifier, actual);
                    ctx.pipeline().remove(ctx.name());

                    super.channelActive(ctx);
                }
            });
        }
    }

    @Nullable
    private ChannelHandler createPcapLoggingHandler(Channel ch, String qualifier) {
        String pattern = configuration.getPcapLoggingPathPattern();
        if (pattern == null) {
            return null;
        }

        String path = pattern;
        path = path.replace("{qualifier}", qualifier);
        if (ch.localAddress() != null) {
            path = path.replace("{localAddress}", resolveIfNecessary(ch.localAddress()));
        }
        if (ch.remoteAddress() != null) {
            path = path.replace("{remoteAddress}", resolveIfNecessary(ch.remoteAddress()));
        }
        if (udpBootstrap != null && ch instanceof QuicStreamChannel qsc) {
            path = path.replace("{localAddress}", resolveIfNecessary(qsc.parent().localSocketAddress()));
            path = path.replace("{remoteAddress}", resolveIfNecessary(qsc.parent().remoteSocketAddress()));
        }
        path = path.replace("{random}", Long.toHexString(ThreadLocalRandom.current().nextLong()));
        path = path.replace("{timestamp}", Instant.now().toString());

        path = path.replace(':', '_'); // for windows

        log.warn("Logging *full* request data, as configured. This will contain sensitive information! Path: '{}'", path);

        try {
            PcapWriteHandler.Builder builder = PcapWriteHandler.builder();

            if (udpBootstrap != null && ch instanceof QuicStreamChannel qsc) {
                builder.forceTcpChannel((InetSocketAddress) qsc.parent().localSocketAddress(), (InetSocketAddress) qsc.parent().remoteSocketAddress(), true);
            }

            return builder.build(new FileOutputStream(path));
        } catch (FileNotFoundException e) {
            log.warn("Failed to create target pcap at '{}', not logging.", path, e);
            return null;
        }
    }

    /**
     * Force resolution of the given address, and then transform it to string. This prevents any potential user data
     * appearing in the file path
     */
    private String resolveIfNecessary(SocketAddress address) {
        if (address instanceof InetSocketAddress socketAddress) {
            if (socketAddress.isUnresolved()) {
                // try resolution
                socketAddress = new InetSocketAddress(socketAddress.getHostString(), socketAddress.getPort());
                if (socketAddress.isUnresolved()) {
                    // resolution failed, bail
                    return "unresolved";
                }
            }
            return socketAddress.getAddress().getHostAddress() + ':' + socketAddress.getPort();
        }
        String s = address.toString();
        if (s.contains("/")) {
            return "weird";
        }
        return s;
    }

    /**
     * Initializer for HTTP2 multiplexing, called either in h2c mode, or after ALPN in TLS. The
     * channel should already contain a {@link #makeFrameCodec() frame codec} that does the HTTP2
     * parsing, this method adds the handlers that do multiplexing, error handling, etc.
     *
     * @param pool The pool to add the connection to once the handshake is done
     * @param ch The plaintext channel
     * @param connectionCustomizer Customizer for the connection
     */
    private void initHttp2(Pool pool, Channel ch, NettyClientCustomizer connectionCustomizer) {
        Http2MultiplexHandler multiplexHandler = new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
            @Override
            protected void initChannel(@NonNull Http2StreamChannel ch) throws Exception {
                log.warn("Server opened HTTP2 stream {}, closing immediately", ch.stream().id());
                ch.close();
            }
        }, new ChannelInitializer<Http2StreamChannel>() {
            @Override
            protected void initChannel(@NonNull Http2StreamChannel ch) throws Exception {
                // discard any response data for the upgrade request
                ch.close();
            }
        });
        Pool.Http2ConnectionHolder connectionHolder = pool.new Http2ConnectionHolder(ch, connectionCustomizer);
        ch.pipeline().addLast(multiplexHandler);
        ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_SETTINGS, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                if (msg instanceof Http2SettingsFrame) {
                    ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_HTTP2_SETTINGS);
                    ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_INITIAL_ERROR);
                    connectionHolder.init();
                    return;
                } else {
                    log.warn("Premature frame: {}", msg.getClass());
                }

                super.channelRead(ctx, msg);
            }
        });
        // stream frames should be handled by the multiplexer
        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                ctx.read();
            }

            @Override
            public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                if (msg instanceof Http2SettingsAckFrame) {
                    // this is fine
                    return;
                }
                if (msg instanceof Http2GoAwayFrame goAway) {
                    connectionHolder.windDownConnection();
                    if (log.isDebugEnabled()) {
                        // include the debug content, but at most 64 bytes for safety
                        byte[] debug = new byte[Math.min(64, goAway.content().readableBytes())];
                        goAway.content().readBytes(debug);
                        log.debug("Server sent GOAWAY frame. errorCode={} base64(content)={}", goAway.errorCode(), Base64.getEncoder().encodeToString(debug));
                    }
                    goAway.release();
                    return;
                }

                log.warn("Unexpected message on HTTP2 connection channel: {}", msg);
                ReferenceCountUtil.release(msg);
                ctx.read();
            }
        });
    }

    private <E extends HttpClientException> E decorate(E exc) {
        return HttpClientExceptionUtils.populateServiceId(exc, informationalServiceId, configuration);
    }

    abstract static class CustomizerAwareInitializer extends ChannelInitializer<Channel> {
        NettyClientCustomizer bootstrappedCustomizer;
    }

    /**
     * Initializer for TLS channels. After ALPN we will proceed either with
     * {@link #initHttp1(Channel)} or {@link #initHttp2(Pool, Channel, NettyClientCustomizer)}.
     */
    private final class AdaptiveAlpnChannelInitializer extends CustomizerAwareInitializer {
        private final Pool pool;

        private final SslContext sslContext;
        private final String host;
        private final int port;

        AdaptiveAlpnChannelInitializer(Pool pool,
                                       SslContext sslContext,
                                       String host,
                                       int port) {
            this.pool = pool;
            this.sslContext = sslContext;
            this.host = host;
            this.port = port;
        }

        /**
         * @param ch The channel
         */
        @Override
        protected void initChannel(@NonNull Channel ch) {
            NettyClientCustomizer channelCustomizer = bootstrappedCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION);

            insertPcapLoggingHandlerLazy(ch, "outer");

            configureProxy(ch.pipeline(), true, host, port);

            ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_SSL, configureSslHandler(sslContext.newHandler(ch.alloc(), host, port)));

            insertPcapLoggingHandlerLazy(ch, "tls-unwrapped");

            ch.pipeline()
                .addLast(
                    ChannelPipelineCustomizer.HANDLER_HTTP2_PROTOCOL_NEGOTIATOR,
                    // if the server doesn't do ALPN, fall back to HTTP 1
                    new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                        @Override
                        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                                ctx.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, makeFrameCodec());
                                initHttp2(pool, ctx.channel(), channelCustomizer);
                            } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                                initHttp1(ctx.channel());
                                pool.new Http1ConnectionHolder(ch, channelCustomizer).init(false);
                                ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_INITIAL_ERROR);
                            } else {
                                ctx.close();
                                throw decorate(new HttpClientException("Unknown Protocol: " + protocol));
                            }
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt instanceof SslHandshakeCompletionEvent event) {
                                if (!event.isSuccess()) {
                                    InitialConnectionErrorHandler.setFailureCause(ctx.channel(), event.cause());
                                }
                            }
                            super.userEventTriggered(ctx, evt);
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            // let the HANDLER_INITIAL_ERROR handle the failure
                            if (cause instanceof DecoderException && cause.getCause() instanceof SSLException) {
                                // unwrap DecoderException
                                cause = cause.getCause();
                            }
                            ctx.fireExceptionCaught(cause);
                        }
                    })
                .addLast(ChannelPipelineCustomizer.HANDLER_INITIAL_ERROR, pool.initialErrorHandler);

            channelCustomizer.onInitialPipelineBuilt();
        }
    }

    /**
     * Initializer for H2C connections. Will proceed with
     * {@link #initHttp2(Pool, Channel, NettyClientCustomizer)} when the upgrade is done.
     */
    private final class Http2UpgradeInitializer extends CustomizerAwareInitializer {
        private final Pool pool;

        Http2UpgradeInitializer(Pool pool) {
            this.pool = pool;
        }

        @Override
        protected void initChannel(@NonNull Channel ch) throws Exception {
            NettyClientCustomizer connectionCustomizer = bootstrappedCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION);

            insertPcapLoggingHandlerLazy(ch, "outer");

            Http2FrameCodec frameCodec = makeFrameCodec();

            HttpClientCodec sourceCodec = new HttpClientCodec();
            Http2ClientUpgradeCodec upgradeCodec = new Http2ClientUpgradeCodec(frameCodec,
                new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel ch) throws Exception {
                        ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, frameCodec);
                        initHttp2(pool, ch, connectionCustomizer);
                    }
                });
            HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536);

            ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC, sourceCodec);
            ch.pipeline().addLast(upgradeHandler);

            ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_UPGRADE_REQUEST, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(@NonNull ChannelHandlerContext ctx) throws Exception {
                    DefaultFullHttpRequest upgradeRequest =
                        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER);

                    // Set HOST header as the remote peer may require it.
                    upgradeRequest.headers().set(HttpHeaderNames.HOST, pool.requestKey.getHost() + ':' + pool.requestKey.getPort());
                    ctx.writeAndFlush(upgradeRequest);
                    ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_HTTP2_UPGRADE_REQUEST);
                    // read the upgrade response
                    ctx.read();

                    super.channelActive(ctx);
                }
            });
            ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_INITIAL_ERROR, pool.initialErrorHandler);

            connectionCustomizer.onInitialPipelineBuilt();
        }
    }

    private final class Http3ChannelInitializer extends ChannelOutboundHandlerAdapter {
        private final Pool pool;

        private final String host;
        private final int port;

        private NettyClientCustomizer bootstrappedCustomizer;

        Http3ChannelInitializer(Pool pool, String host, int port) {
            this.pool = pool;
            this.host = host;
            this.port = port;
        }

        // delay channel initialization until bind is complete. This is required so that we can see
        // the local address
        @Override
        public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
            ChannelPromise downstreamPromise = ctx.newPromise();
            super.bind(ctx, localAddress, downstreamPromise);
            downstreamPromise.addListener(future -> {
                if (future.isSuccess()) {
                    try {
                        initChannel(promise.channel());
                        ctx.pipeline().remove(this);
                        promise.setSuccess();
                    } catch (Exception e) {
                        promise.setFailure(e);
                    }
                } else {
                    promise.setFailure(future.cause());
                }
            });
        }

        private void initChannel(Channel ch) {
            NettyClientCustomizer channelCustomizer = bootstrappedCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION);

            insertPcapLoggingHandlerLazy(ch, "outer");

            ch.pipeline()
                .addLast(Http3.newQuicClientCodecBuilder()
                    .sslEngineProvider(c -> ((QuicSslContext) http3SslContext).newEngine(c.alloc(), host, port))
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .build())
                .addLast(ChannelPipelineCustomizer.HANDLER_INITIAL_ERROR, pool.initialErrorHandler);

            channelCustomizer.onInitialPipelineBuilt();

            QuicChannel.newBootstrap(ch)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                        QuicChannel quicChannel = (QuicChannel) ctx.channel();
                        ctx.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, new Http3ClientConnectionHandler(
                            // control stream handler
                            new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    if (msg instanceof Http3SettingsFrame) {
                                        ch.pipeline().remove(ChannelPipelineCustomizer.HANDLER_INITIAL_ERROR);
                                        pool.new Http3ConnectionHolder(ch, quicChannel, channelCustomizer).init();
                                    }
                                    super.channelRead(ctx, msg);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                    ch.pipeline().remove(ChannelPipelineCustomizer.HANDLER_INITIAL_ERROR);
                                    ch.close();
                                    pool.onNewConnectionFailure(cause);
                                }
                            },
                            null,
                            null,
                            null,
                            false
                        ));
                        ctx.pipeline().remove(this);
                    }
                })
                .remoteAddress(new InetSocketAddress(this.host, this.port))
                .localAddress(ch.localAddress())
                .connect()
                .addListener((GenericFutureListener<Future<QuicChannel>>) future -> {
                    if (!future.isSuccess()) {
                        pool.onNewConnectionFailure(future.cause());
                    }
                });
        }
    }

    /**
     * Handle for a pooled connection. One pool handle generally corresponds to one request, and
     * once the request and response are done, the handle is {@link #release() released} and a new
     * request can claim the same connection.
     */
    public abstract static class PoolHandle {
        private static final Supplier<ResourceLeakDetector<PoolHandle>> LEAK_DETECTOR = SupplierUtil.memoized(() ->
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(PoolHandle.class));

        final boolean http2;
        final Channel channel;

        boolean released = false;

        private final ResourceLeakTracker<PoolHandle> tracker = LEAK_DETECTOR.get().track(this);

        private PoolHandle(boolean http2, Channel channel) {
            this.http2 = http2;
            this.channel = channel;
        }

        public final Channel channel() {
            return channel;
        }

        public final boolean http2() {
            return http2;
        }

        /**
         * Prevent this connection from being reused, e.g. because garbage was written because of
         * an error.
         */
        public abstract void taint();

        /**
         * Close this connection or release it back to the pool.
         */
        public void release() {
            if (released) {
                throw new IllegalStateException("Already released");
            }
            released = true;
            if (tracker != null) {
                tracker.close(this);
            }
        }

        /**
         * Whether this connection may be returned to a connection pool (i.e. should be kept
         * keepalive).
         *
         * @return Whether this connection may be reused
         */
        public abstract boolean canReturn();

        /**
         * Notify any {@link NettyClientCustomizer} that the request pipeline has been built.
         */
        public abstract void notifyRequestPipelineBuilt();
    }

    /**
     * This class represents one pool, and matches to exactly one
     * {@link DefaultHttpClient.RequestKey} (i.e. host, port and
     * protocol are the same for one pool).
     * <p>
     * The superclass {@link PoolResizer} handles pool size management, this class just implements
     * the HTTP parts.
     */
    private final class Pool extends PoolResizer {
        private final DefaultHttpClient.RequestKey requestKey;

        /**
         * {@link ChannelHandler} that is added to a connection to report failures during
         * handshakes. It's removed once the connection is established and processes requests.
         */
        private final InitialConnectionErrorHandler initialErrorHandler = new InitialConnectionErrorHandler() {
            @Override
            protected void onNewConnectionFailure(@Nullable Throwable cause) throws Exception {
                Pool.this.onNewConnectionFailure(cause);
            }
        };

        Pool(DefaultHttpClient.RequestKey requestKey) {
            super(log, configuration.getConnectionPoolConfiguration());
            this.requestKey = requestKey;
        }

        ExecutionFlow<PoolHandle> acquire(@Nullable BlockHint blockHint) {
            PendingRequest sink = new PendingRequest(blockHint);
            addPendingRequest(sink);
            Optional<Duration> acquireTimeout = configuration.getConnectionPoolConfiguration().getAcquireTimeout();
            //noinspection OptionalIsPresent
            if (acquireTimeout.isPresent()) {
                return sink.flow().timeout(acquireTimeout.get(), group, (v, e) -> {
                    if (v != null) {
                        v.release();
                    }
                });
            } else {
                return sink.flow();
            }
        }

        @Override
        void onNewConnectionFailure(@Nullable Throwable error) throws Exception {
            super.onNewConnectionFailure(error);
            // to avoid an infinite loop, fail one pending request.
            PendingRequest pending = pollPendingRequest();
            if (pending != null) {
                HttpClientException wrapped;
                if (error == null) {
                    // no failure observed, but channel closed
                    wrapped = new HttpClientException("Unknown connect error");
                } else {
                    wrapped = new HttpClientException("Connect Error: " + error.getMessage(), error);
                }
                if (pending.tryCompleteExceptionally(decorate(wrapped))) {
                    // no need to log
                    return;
                }
            }
            log.error("Failed to connect to remote", error);
        }

        @Override
        void openNewConnection(@Nullable BlockHint blockHint) throws Exception {
            // open a new connection
            ChannelFuture channelFuture = openConnectionFuture();
            if (blockHint != null && blockHint.blocks(channelFuture.channel().eventLoop())) {
                channelFuture.channel().close();
                onNewConnectionFailure(BlockHint.createException());
                return;
            }
            withPropagation(channelFuture, future -> {
                if (!future.isSuccess()) {
                    onNewConnectionFailure(future.cause());
                }
            });
        }

        private ChannelFuture openConnectionFuture() {
            CustomizerAwareInitializer initializer;
            if (requestKey.isSecure()) {
                if (httpVersion.isHttp3()) {
                    Http3ChannelInitializer channelInitializer = new Http3ChannelInitializer(this, requestKey.getHost(), requestKey.getPort());
                    Bootstrap localBootstrap = udpBootstrap.clone()
                        .handler(channelInitializer)
                        .localAddress(0);
                    channelInitializer.bootstrappedCustomizer = clientCustomizer.specializeForBootstrap(localBootstrap);
                    return localBootstrap.bind();
                }

                initializer = new AdaptiveAlpnChannelInitializer(
                    this,
                    buildSslContext(requestKey),
                    requestKey.getHost(),
                    requestKey.getPort()
                );
            } else {
                initializer = switch (httpVersion.getPlaintextMode()) {
                    case HTTP_1 -> new CustomizerAwareInitializer() {
                        @Override
                        protected void initChannel(@NonNull Channel ch) throws Exception {
                            insertPcapLoggingHandlerLazy(ch, "outer");
                            configureProxy(ch.pipeline(), false, requestKey.getHost(), requestKey.getPort());
                            initHttp1(ch);
                            ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_ACTIVITY_LISTENER, new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(@NonNull ChannelHandlerContext ctx) throws Exception {
                                    super.channelActive(ctx);
                                    ctx.pipeline().remove(this);
                                    NettyClientCustomizer channelCustomizer = bootstrappedCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION);
                                    new Http1ConnectionHolder(ch, channelCustomizer).init(true);
                                }
                            });
                        }
                    };
                    case H2C -> new Http2UpgradeInitializer(this);
                };
            }
            return doConnect(requestKey, initializer);
        }

        public void shutdown() {
            forEachConnection(c -> ((ConnectionHolder) c).channel.close());
        }

        /**
         * Base class for one HTTP1/HTTP2 connection.
         */
        abstract class ConnectionHolder extends ResizerConnection {
            final Channel channel;
            final NettyClientCustomizer connectionCustomizer;
            /**
             * Future for the scheduled task that runs when the configured time-to-live for the
             * connection passes.
             */
            @Nullable
            ScheduledFuture<?> ttlFuture;
            volatile boolean windDownConnection = false;

            private ReadTimeoutHandler readTimeoutHandler;

            ConnectionHolder(Channel channel, NettyClientCustomizer connectionCustomizer) {
                this.channel = channel;
                this.connectionCustomizer = connectionCustomizer;
            }

            /**
             * Reset the read timeout, i.e. start it from 0 again.
             */
            private void resetReadTimeout() {
                if (readTimeoutHandler != null) {
                    readTimeoutHandler.resetReadTimeout();
                }
            }

            /**
             * Add connection-level timeout-related handlers to the channel
             * (read timeout, TTL, ...).
             *
             * @param before Reference handler name, the timeout handlers will be placed before
             *               this handler.
             */
            final void addTimeoutHandlers(String before) {
                // read timeout handles timeouts *during* a request
                configuration.getReadTimeout()
                    .ifPresent(dur -> {
                        ReadTimeoutHandler readTimeoutHandler = new ReadTimeoutHandler(dur.toNanos(), TimeUnit.NANOSECONDS) {
                            @Override
                            protected void readTimedOut(ChannelHandlerContext ctx) {
                                if (hasLiveRequests()) {
                                    windDownConnection = true;
                                    fireReadTimeout(ctx);
                                    ctx.close();
                                }
                            }
                        };
                        this.readTimeoutHandler = readTimeoutHandler;
                        channel.pipeline().addBefore(before, ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT, readTimeoutHandler);
                    });
                // pool idle timeout happens *outside* a request
                configuration.getConnectionPoolIdleTimeout()
                    .ifPresent(dur -> channel.pipeline().addBefore(before, ChannelPipelineCustomizer.HANDLER_IDLE_STATE, new ReadTimeoutHandler(dur.toNanos(), TimeUnit.NANOSECONDS) {
                        @Override
                        protected void readTimedOut(ChannelHandlerContext ctx) {
                            if (!hasLiveRequests()) {
                                windDownConnection = true;
                                ctx.close();
                            }
                        }
                    }));
                configuration.getConnectTtl().ifPresent(ttl ->
                    ttlFuture = channel.eventLoop().schedule(this::windDownConnection, ttl.toNanos(), TimeUnit.NANOSECONDS));
                channel.pipeline().addBefore(before, "connection-cleaner", new ChannelInboundHandlerAdapter() {
                    boolean inactiveCalled = false;

                    @Override
                    public void channelInactive(@NonNull ChannelHandlerContext ctx) throws Exception {
                        super.channelInactive(ctx);
                        if (!inactiveCalled) {
                            inactiveCalled = true;
                            onInactive();
                        }
                    }

                    @Override
                    public void handlerRemoved(ChannelHandlerContext ctx) {
                        if (!inactiveCalled) {
                            inactiveCalled = true;
                            onInactive();
                        }
                    }
                });
            }

            /**
             * Stop accepting new requests on this connection, but finish up the running requests
             * if possible.
             */
            void windDownConnection() {
                windDownConnection = true;
            }

            /**
             * Send the finished pool handle to the given requester, if possible.
             *
             * @param sink The request for a pool handle
             * @param ph The pool handle
             */
            final void emitPoolHandle(PendingRequest sink, PoolHandle ph) {
                if (!sink.tryComplete(ph)) {
                    ph.release();
                } else {
                    if (!configuration.getConnectionPoolConfiguration().isEnabled()) {
                        // if pooling is off, release the connection after this.
                        windDownConnection();
                    }
                }
            }

            @Override
            public boolean dispatch(PendingRequest sink) {
                if (!tryEarmarkForRequest()) {
                    return false;
                }

                BlockHint blockHint = sink.blockHint;
                if (blockHint != null && blockHint.blocks(channel.eventLoop())) {
                    sink.tryCompleteExceptionally(BlockHint.createException());
                    return true;
                }
                if (channel.eventLoop().inEventLoop()) {
                    resetReadTimeout();
                    dispatch0(sink);
                } else {
                    channel.eventLoop().execute(() -> {
                        resetReadTimeout();
                        dispatch0(sink);
                    });
                }
                return true;
            }

            /**
             * <b>Called on event loop only.</b> Dispatch a stream/connection to the given pool
             * handle request.
             *
             * @param sink The request for a pool handle
             */
            abstract void dispatch0(PendingRequest sink);

            /**
             * Try to add a new request to this connection. This is called outside the event loop,
             * and if this succeeds, we will proceed with a {@link #dispatch0} call <i>on</i> the
             * event loop.
             *
             * @return {@code true} if the request may be added to this connection
             */
            abstract boolean tryEarmarkForRequest();

            /**
             * @return {@code true} iff there are any requests running on this connection.
             */
            abstract boolean hasLiveRequests();

            /**
             * Send a read timeout exception to all requests on this connection.
             *
             * @param ctx The connection-level channel handler context to use.
             */
            abstract void fireReadTimeout(ChannelHandlerContext ctx);

            /**
             * Called when the connection becomes inactive, i.e. on disconnect.
             */
            void onInactive() {
                if (ttlFuture != null) {
                    ttlFuture.cancel(false);
                }
                windDownConnection = true;
            }
        }

        final class Http1ConnectionHolder extends ConnectionHolder {
            private final AtomicBoolean earmarkedOrLive = new AtomicBoolean(false);
            private volatile boolean hasLiveRequest = false;

            Http1ConnectionHolder(Channel channel, NettyClientCustomizer connectionCustomizer) {
                super(channel, connectionCustomizer);
            }

            void init(boolean fireInitialPipelineBuilt) {
                addTimeoutHandlers(
                    requestKey.isSecure() ?
                        ChannelPipelineCustomizer.HANDLER_SSL :
                        ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC
                );

                if (fireInitialPipelineBuilt) {
                    connectionCustomizer.onInitialPipelineBuilt();
                }
                connectionCustomizer.onStreamPipelineBuilt();

                onNewConnectionEstablished1(this);
            }

            @Override
            boolean tryEarmarkForRequest() {
                return !windDownConnection && earmarkedOrLive.compareAndSet(false, true);
            }

            @Override
            boolean hasLiveRequests() {
                return hasLiveRequest;
            }

            @Override
            void fireReadTimeout(ChannelHandlerContext ctx) {
                ctx.fireExceptionCaught(ReadTimeoutException.INSTANCE);
            }

            @Override
            void dispatch0(PendingRequest sink) {
                if (!channel.isActive()) {
                    // make sure the request isn't dispatched to this connection again
                    windDownConnection();

                    returnPendingRequest(sink);
                    return;
                }
                hasLiveRequest = true;
                PoolHandle ph = new PoolHandle(false, channel) {
                    final ChannelHandlerContext lastContext = channel.pipeline().lastContext();

                    @Override
                    public void taint() {
                        windDownConnection = true;
                    }

                    @Override
                    public void release() {
                        super.release();
                        if (!windDownConnection) {
                            ChannelHandlerContext newLast = channel.pipeline().lastContext();
                            if (lastContext != newLast) {
                                log.warn("BUG - Handler not removed: {}", newLast);
                                taint();
                            }
                        }
                        if (!windDownConnection) {
                            hasLiveRequest = false;
                            earmarkedOrLive.set(false);
                            markConnectionAvailable();
                        } else {
                            channel.close();
                        }
                    }

                    @Override
                    public boolean canReturn() {
                        return !windDownConnection;
                    }

                    @Override
                    public void notifyRequestPipelineBuilt() {
                        connectionCustomizer.onRequestPipelineBuilt();
                    }
                };
                emitPoolHandle(sink, ph);
            }

            private void returnPendingRequest(PendingRequest sink) {
                // failed, but the pending request may still work on another connection.
                addPendingRequest(sink);
                hasLiveRequest = false;
                earmarkedOrLive.set(false);
            }

            @Override
            void windDownConnection() {
                super.windDownConnection();
                if (!hasLiveRequest) {
                    channel.close();
                }
            }

            @Override
            void onInactive() {
                super.onInactive();
                onConnectionInactive1(this);
            }
        }

        class Http2ConnectionHolder extends ConnectionHolder {
            private final AtomicInteger earmarkedOrLiveRequests = new AtomicInteger(0);
            private final AtomicInteger liveRequests = new AtomicInteger(0);

            Http2ConnectionHolder(Channel channel, NettyClientCustomizer customizer) {
                super(channel, customizer);
            }

            void init() {
                addTimeoutHandlers();

                connectionCustomizer.onStreamPipelineBuilt();

                onNewConnectionEstablished2(this);
            }

            void addTimeoutHandlers() {
                addTimeoutHandlers(
                    requestKey.isSecure() ?
                        ChannelPipelineCustomizer.HANDLER_SSL :
                        ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION
                );

                HttpClientConfiguration.Http2ClientConfiguration http2Configuration = configuration.getHttp2Configuration();
                if (http2Configuration != null) {
                    long read = toNanos(http2Configuration.getPingIntervalRead());
                    long write = toNanos(http2Configuration.getPingIntervalWrite());
                    long idle = toNanos(http2Configuration.getPingIntervalIdle());
                    if (read > 0 || write > 0 || idle > 0) {
                        channel.pipeline().addAfter(
                            ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION,
                            ChannelPipelineCustomizer.HANDLER_HTTP2_PING_SENDER,
                            new Http2PingSender(read, write, idle, TimeUnit.NANOSECONDS));
                    }
                }
            }

            private static long toNanos(@Nullable Duration timeout) {
                if (timeout == null) {
                    return 0;
                }
                long nanos = timeout.toNanos();
                return nanos < 0 ? 0 : nanos;
            }

            @Override
            boolean tryEarmarkForRequest() {
                return !windDownConnection && incrementWithLimit(earmarkedOrLiveRequests, configuration.getConnectionPoolConfiguration().getMaxConcurrentRequestsPerHttp2Connection());
            }

            @Override
            boolean hasLiveRequests() {
                return liveRequests.get() > 0;
            }

            @Override
            void fireReadTimeout(ChannelHandlerContext ctx) {
                channel.pipeline().fireExceptionCaught(new Http2MultiplexActiveStreamsException(ReadTimeoutException.INSTANCE));
            }

            @Override
            void dispatch0(PendingRequest sink) {
                if (!channel.isActive() || windDownConnection) {
                    // make sure the request isn't dispatched to this connection again
                    windDownConnection();

                    returnPendingRequest(sink);
                    return;
                }
                liveRequests.incrementAndGet();
                withPropagation(openStreamChannel(), (Future<Channel> future) -> {
                    if (future.isSuccess()) {
                        Channel streamChannel = future.get();
                        streamChannel.pipeline()
                            .addLast(new ChannelOutboundHandlerAdapter() {
                                @Override
                                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                                    adaptHeaders(msg);
                                    super.write(ctx, msg, promise);
                                }
                            })
                            .addLast(createFrameToHttpObjectCodec())
                            .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_DECOMPRESSOR, new HttpContentDecompressor());
                        NettyClientCustomizer streamCustomizer = connectionCustomizer.specializeForChannel(streamChannel, NettyClientCustomizer.ChannelRole.HTTP2_STREAM);
                        PoolHandle ph = new PoolHandle(true, streamChannel) {
                            @Override
                            public void taint() {
                                // do nothing, we don't reuse stream channels
                            }

                            @Override
                            public void release() {
                                super.release();
                                streamChannel.close();
                                int newCount = liveRequests.decrementAndGet();
                                earmarkedOrLiveRequests.decrementAndGet();
                                if (windDownConnection && newCount <= 0) {
                                    Http2ConnectionHolder.this.channel.close();
                                } else {
                                    markConnectionAvailable();
                                }
                            }

                            @Override
                            public boolean canReturn() {
                                return true;
                            }

                            @Override
                            public void notifyRequestPipelineBuilt() {
                                streamCustomizer.onRequestPipelineBuilt();
                            }
                        };
                        emitPoolHandle(sink, ph);
                    } else {
                        log.debug("Failed to open http2 stream", future.cause());
                        liveRequests.decrementAndGet();
                        returnPendingRequest(sink);
                    }
                });
            }

            @NonNull
            ChannelHandler createFrameToHttpObjectCodec() {
                return new Http2StreamFrameToHttpObjectCodec(false);
            }

            Future<? extends Channel> openStreamChannel() {
                return new Http2StreamChannelBootstrap(channel).open();
            }

            void adaptHeaders(Object msg) {
                if (msg instanceof Http2HeadersFrame hf) {
                    if (requestKey.isSecure()) {
                        hf.headers().scheme(HttpScheme.HTTPS.name());
                    } else {
                        hf.headers().scheme(HttpScheme.HTTP.name());
                    }
                }
            }

            private void returnPendingRequest(PendingRequest sink) {
                // failed, but the pending request may still work on another connection.
                addPendingRequest(sink);
                earmarkedOrLiveRequests.decrementAndGet();
            }

            @Override
            void windDownConnection() {
                super.windDownConnection();
                if (liveRequests.get() == 0) {
                    channel.close();
                }
            }

            @Override
            void onInactive() {
                super.onInactive();
                onConnectionInactive2(this);
            }
        }

        final class Http3ConnectionHolder extends Http2ConnectionHolder {
            private final Channel udpChannel;
            private final QuicChannel quicChannel;

            Http3ConnectionHolder(Channel channel, QuicChannel quicChannel, NettyClientCustomizer customizer) {
                super(quicChannel, customizer);
                this.udpChannel = channel;
                this.quicChannel = quicChannel;
            }

            @Override
            void adaptHeaders(Object msg) {
                if (msg instanceof Http3HeadersFrame hf) {
                    if (requestKey.isSecure()) {
                        hf.headers().scheme(HttpScheme.HTTPS.name());
                    } else {
                        hf.headers().scheme(HttpScheme.HTTP.name());
                    }
                }
            }

            @Override
            void addTimeoutHandlers() {
                addTimeoutHandlers(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION);
            }

            @Override
            ChannelHandler createFrameToHttpObjectCodec() {
                return new Http3FrameToHttpObjectCodec(false);
            }

            @Override
            Future<? extends Channel> openStreamChannel() {
                return Http3.newRequestStream(quicChannel, new Http3RequestStreamInitializer() {
                    @Override
                    protected void initRequestStream(QuicStreamChannel ch) {
                        // do nothing, channel is initialized in the future handler
                    }
                });
            }

            @Override
            void onInactive() {
                super.onInactive();
                udpChannel.close();
            }
        }
    }
}
