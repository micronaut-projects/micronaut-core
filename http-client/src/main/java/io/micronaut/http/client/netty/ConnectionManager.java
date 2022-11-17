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
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.netty.ssl.NettyClientSslBuilder;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.scheduling.instrument.Instrumentation;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
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
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.logging.LoggingHandler;
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
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Connection manager for {@link DefaultHttpClient}. This class manages the lifecycle of netty
 * channels (wrapped in {@link PoolHandle}s), including pooling and timeouts.
 */
@Internal
class ConnectionManager {
    final InvocationInstrumenter instrumenter;

    private final HttpVersionSelection httpVersion;
    private final Logger log;
    private final Map<DefaultHttpClient.RequestKey, Pool> pools = new ConcurrentHashMap<>();
    private EventLoopGroup group;
    private final boolean shutdownGroup;
    private final ThreadFactory threadFactory;
    private final ChannelFactory<? extends Channel> socketChannelFactory;
    private Bootstrap bootstrap;
    private final HttpClientConfiguration configuration;
    private final SslContext sslContext;
    private final NettyClientCustomizer clientCustomizer;
    private final String informationalServiceId;

    /**
     * Copy constructor used by the test suite to patch this manager.
     *
     * @param from Original connection manager
     */
    ConnectionManager(ConnectionManager from) {
        this.instrumenter = from.instrumenter;
        this.httpVersion = from.httpVersion;
        this.log = from.log;
        this.group = from.group;
        this.shutdownGroup = from.shutdownGroup;
        this.threadFactory = from.threadFactory;
        this.socketChannelFactory = from.socketChannelFactory;
        this.bootstrap = from.bootstrap;
        this.configuration = from.configuration;
        this.sslContext = from.sslContext;
        this.clientCustomizer = from.clientCustomizer;
        this.informationalServiceId = from.informationalServiceId;
    }

    ConnectionManager(
        Logger log,
        @Nullable EventLoopGroup eventLoopGroup,
        @Nullable ThreadFactory threadFactory,
        HttpClientConfiguration configuration,
        @Nullable  HttpVersionSelection httpVersion,
        InvocationInstrumenter instrumenter,
        ChannelFactory<? extends Channel> socketChannelFactory,
        NettyClientSslBuilder nettyClientSslBuilder,
        NettyClientCustomizer clientCustomizer,
        String informationalServiceId) {

        if (httpVersion == null) {
            httpVersion = HttpVersionSelection.forClientConfiguration(configuration);
        }

        this.log = log;
        this.httpVersion = httpVersion;
        this.threadFactory = threadFactory;
        this.socketChannelFactory = socketChannelFactory;
        this.configuration = configuration;
        this.instrumenter = instrumenter;
        this.clientCustomizer = clientCustomizer;
        this.informationalServiceId = informationalServiceId;

        this.sslContext = nettyClientSslBuilder.build(configuration.getSslConfiguration(), httpVersion);

        if (eventLoopGroup != null) {
            group = eventLoopGroup;
            shutdownGroup = false;
        } else {
            group = createEventLoopGroup(configuration, threadFactory);
            shutdownGroup = true;
        }

        initBootstrap();

        Optional<Duration> connectTimeout = configuration.getConnectTimeout();
        connectTimeout.ifPresent(duration -> bootstrap.option(
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
                group = new NioEventLoopGroup(NettyThreadFactory.DEFAULT_EVENT_LOOP_THREADS, threadFactory);
            } else {

                group = new NioEventLoopGroup();
            }
        }
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
    List<Channel> getChannels() {
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
    int liveRequestCount() {
        AtomicInteger count = new AtomicInteger();
        for (Pool pool : pools.values()) {
            pool.forEachConnection(c -> {
                if (c instanceof Pool.Http1ConnectionHolder) {
                    if (((Pool.Http1ConnectionHolder) c).hasLiveRequests()) {
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
    public void start() {
        // only need to start new group if it's managed by us
        if (shutdownGroup) {
            group = createEventLoopGroup(configuration, threadFactory);
            initBootstrap(); // rebuild bootstrap with new group
        }
    }

    private void initBootstrap() {
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(group)
            .channelFactory(socketChannelFactory)
            .option(ChannelOption.SO_KEEPALIVE, true);
    }

    /**
     * @see DefaultHttpClient#stop()
     */
    public void shutdown() {
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
    }

    /**
     * @see DefaultHttpClient#isRunning()
     *
     * @return Whether this connection manager is still running and can serve requests
     */
    public boolean isRunning() {
        return !group.isShutdown();
    }

    /**
     * Use the bootstrap to connect to the given host. Also does some proxy setup. This method is
     * protected: The test suite overrides it to return embedded channels instead.
     *
     * @param requestKey The host to connect to
     * @param channelInitializer The initializer to use
     * @return Future that terminates when the TCP connection is established.
     */
    protected ChannelFuture doConnect(DefaultHttpClient.RequestKey requestKey, ChannelInitializer<?> channelInitializer) {
        String host = requestKey.getHost();
        int port = requestKey.getPort();
        Bootstrap localBootstrap = bootstrap.clone();
        Proxy proxy = configuration.resolveProxy(requestKey.isSecure(), host, port);
        if (proxy.type() != Proxy.Type.DIRECT) {
            localBootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
        }
        localBootstrap.handler(channelInitializer);
        return localBootstrap.connect(host, port);
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
                throw customizeException(new HttpClientException("Cannot send HTTPS request. SSL is disabled"));
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
     * @param blockHint Optional information about what threads are blocked for this connection request
     * @return A mono that will complete once the channel is ready for transmission
     */
    Mono<PoolHandle> connect(DefaultHttpClient.RequestKey requestKey, @Nullable BlockHint blockHint) {
        return pools.computeIfAbsent(requestKey, Pool::new).acquire(blockHint);
    }

    /**
     * Connect to a remote websocket. The given {@link ChannelHandler} is added to the pipeline
     * when the handshakes complete.
     *
     * @param requestKey The remote to connect to
     * @param handler The websocket message handler
     * @return A mono that will complete when the handshakes complete
     */
    Mono<?> connectForWebsocket(DefaultHttpClient.RequestKey requestKey, ChannelHandler handler) {
        Sinks.Empty<Object> initial = new CancellableMonoSink<>(null);

        ChannelFuture connectFuture = doConnect(requestKey, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(@NonNull Channel ch) {
                addLogHandler(ch);

                SslContext sslContext = buildSslContext(requestKey);
                if (sslContext != null) {
                    SslHandler sslHandler = sslContext.newHandler(ch.alloc(), requestKey.getHost(), requestKey.getPort());
                    sslHandler.setHandshakeTimeoutMillis(configuration.getSslConfiguration().getHandshakeTimeout().toMillis());
                    ch.pipeline().addLast(sslHandler);
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
                    ch.pipeline().addLast(WebSocketClientCompressionHandler.INSTANCE);
                    ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_WEBSOCKET_CLIENT, handler);
                    clientCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION).onInitialPipelineBuilt();
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
        addInstrumentedListener(connectFuture, future -> {
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

    <V, C extends Future<V>> void addInstrumentedListener(
            Future<V> channelFuture, GenericFutureListener<C> listener) {
        channelFuture.addListener(f -> {
            try (Instrumentation ignored = instrumenter.newInstrumentation()) {
                //noinspection unchecked
                listener.operationComplete((C) f);
            }
        });
    }

    private <E extends HttpClientException> E customizeException(E exc) {
        DefaultHttpClient.customizeException0(configuration, informationalServiceId, exc);
        return exc;
    }

    private Http2FrameCodec makeFrameCodec() {
        Http2FrameCodecBuilder builder = Http2FrameCodecBuilder.forClient();
        configuration.getLogLevel().ifPresent(logLevel -> {
            try {
                final io.netty.handler.logging.LogLevel nettyLevel =
                    io.netty.handler.logging.LogLevel.valueOf(logLevel.name());
                builder.frameLogger(new Http2FrameLogger(nettyLevel, DefaultHttpClient.class));
            } catch (IllegalArgumentException e) {
                throw customizeException(new HttpClientException("Unsupported log level: " + logLevel));
            }
        });
        return builder.build();
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
                final io.netty.handler.logging.LogLevel nettyLevel =
                    io.netty.handler.logging.LogLevel.valueOf(logLevel.name());
                ch.pipeline().addLast(new LoggingHandler(DefaultHttpClient.class, nettyLevel));
            } catch (IllegalArgumentException e) {
                throw customizeException(new HttpClientException("Unsupported log level: " + logLevel));
            }
        });
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
        ch.pipeline().addLast(multiplexHandler);
        ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_SETTINGS, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                if (msg instanceof Http2SettingsFrame) {
                    ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_HTTP2_SETTINGS);
                    ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_INITIAL_ERROR);
                    pool.new Http2ConnectionHolder(ch, connectionCustomizer).init();
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
                log.warn("Unexpected message on HTTP2 connection channel: {}", msg);
                ReferenceCountUtil.release(msg);
                ctx.read();
            }
        });
    }

    /**
     * Initializer for TLS channels. After ALPN we will proceed either with
     * {@link #initHttp1(Channel)} or {@link #initHttp2(Pool, Channel, NettyClientCustomizer)}.
     */
    private final class AdaptiveAlpnChannelInitializer extends ChannelInitializer<Channel> {
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
            NettyClientCustomizer channelCustomizer = clientCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION);

            configureProxy(ch.pipeline(), true, host, port);

            SslHandler sslHandler = sslContext.newHandler(ch.alloc(), host, port);
            sslHandler.setHandshakeTimeoutMillis(configuration.getSslConfiguration().getHandshakeTimeout().toMillis());
            ch.pipeline()
                .addLast(ChannelPipelineCustomizer.HANDLER_SSL, sslHandler)
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
                                throw customizeException(new HttpClientException("Unknown Protocol: " + protocol));
                            }
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt instanceof SslHandshakeCompletionEvent) {
                                SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
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
    private final class Http2UpgradeInitializer extends ChannelInitializer<Channel> {
        private final Pool pool;

        Http2UpgradeInitializer(Pool pool) {
            this.pool = pool;
        }

        @Override
        protected void initChannel(@NonNull Channel ch) throws Exception {
            NettyClientCustomizer connectionCustomizer = clientCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION);

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
                        new DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER);

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

    /**
     * Handle for a pooled connection. One pool handle generally corresponds to one request, and
     * once the request and response are done, the handle is {@link #release() released} and a new
     * request can claim the same connection.
     */
    abstract static class PoolHandle {
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

        /**
         * Prevent this connection from being reused, e.g. because garbage was written because of
         * an error.
         */
        abstract void taint();

        /**
         * Close this connection or release it back to the pool.
         */
        void release() {
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
        abstract boolean canReturn();

        /**
         * Notify any {@link NettyClientCustomizer} that the request pipeline has been built.
         */
        abstract void notifyRequestPipelineBuilt();
    }

    /**
     * This class represents one pool, and matches to exactly one
     * {@link io.micronaut.http.client.netty.DefaultHttpClient.RequestKey} (i.e. host, port and
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

        Mono<PoolHandle> acquire(@Nullable BlockHint blockHint) {
            PoolSink<PoolHandle> sink = new CancellableMonoSink<>(blockHint);
            addPendingRequest(sink);
            Optional<Duration> acquireTimeout = configuration.getConnectionPoolConfiguration().getAcquireTimeout();
            //noinspection OptionalIsPresent
            if (acquireTimeout.isPresent()) {
                return sink.asMono().timeout(acquireTimeout.get(), Schedulers.fromExecutor(group));
            } else {
                return sink.asMono();
            }
        }

        @Override
        void onNewConnectionFailure(@Nullable Throwable error) throws Exception {
            super.onNewConnectionFailure(error);
            // to avoid an infinite loop, fail one pending request.
            Sinks.One<PoolHandle> pending = pollPendingRequest();
            if (pending != null) {
                HttpClientException wrapped;
                if (error == null) {
                    // no failure observed, but channel closed
                    wrapped = new HttpClientException("Unknown connect error");
                } else {
                    wrapped = new HttpClientException("Connect Error: " + error.getMessage(), error);
                }
                if (pending.tryEmitError(customizeException(wrapped)) == Sinks.EmitResult.OK) {
                    // no need to log
                    return;
                }
            }
            log.error("Failed to connect to remote", error);
        }

        @Override
        void openNewConnection(@Nullable BlockHint blockHint) throws Exception {
            // open a new connection
            ChannelInitializer<?> initializer;
            if (requestKey.isSecure()) {
                initializer = new AdaptiveAlpnChannelInitializer(
                    this,
                    buildSslContext(requestKey),
                    requestKey.getHost(),
                    requestKey.getPort()
                );
            } else {
                switch (httpVersion.getPlaintextMode()) {
                    case HTTP_1:
                        initializer = new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(@NonNull Channel ch) throws Exception {
                                configureProxy(ch.pipeline(), false, requestKey.getHost(), requestKey.getPort());
                                initHttp1(ch);
                                ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_ACTIVITY_LISTENER, new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelActive(@NonNull ChannelHandlerContext ctx) throws Exception {
                                        super.channelActive(ctx);
                                        ctx.pipeline().remove(this);
                                        NettyClientCustomizer channelCustomizer = clientCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION);
                                        new Http1ConnectionHolder(ch, channelCustomizer).init(true);
                                    }
                                });
                            }
                        };
                        break;
                    case H2C:
                        initializer = new Http2UpgradeInitializer(this);
                        break;
                    default:
                        throw new AssertionError("Unknown plaintext mode");
                }
            }
            ChannelFuture channelFuture = doConnect(requestKey, initializer);
            if (blockHint != null && blockHint.blocks(channelFuture.channel().eventLoop())) {
                channelFuture.channel().close();
                onNewConnectionFailure(BlockHint.createException());
                return;
            }
            addInstrumentedListener(channelFuture, future -> {
                if (!future.isSuccess()) {
                    onNewConnectionFailure(future.cause());
                }
            });
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

            ConnectionHolder(Channel channel, NettyClientCustomizer connectionCustomizer) {
                this.channel = channel;
                this.connectionCustomizer = connectionCustomizer;
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
                    .ifPresent(dur -> channel.pipeline().addBefore(before, ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT, new ReadTimeoutHandler(dur.toNanos(), TimeUnit.NANOSECONDS) {
                        @Override
                        protected void readTimedOut(ChannelHandlerContext ctx) {
                            if (hasLiveRequests()) {
                                fireReadTimeout(ctx);
                                ctx.close();
                            }
                        }
                    }));
                // pool idle timeout happens *outside* a request
                configuration.getConnectionPoolIdleTimeout()
                    .ifPresent(dur -> channel.pipeline().addBefore(before, ChannelPipelineCustomizer.HANDLER_IDLE_STATE, new ReadTimeoutHandler(dur.toNanos(), TimeUnit.NANOSECONDS) {
                        @Override
                        protected void readTimedOut(ChannelHandlerContext ctx) {
                            if (!hasLiveRequests()) {
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
            final void emitPoolHandle(Sinks.One<PoolHandle> sink, PoolHandle ph) {
                Sinks.EmitResult emitResult = sink.tryEmitValue(ph);
                if (emitResult.isFailure()) {
                    ph.release();
                } else {
                    if (!configuration.getConnectionPoolConfiguration().isEnabled()) {
                        // if pooling is off, release the connection after this.
                        windDownConnection();
                    }
                }
            }

            @Override
            public boolean dispatch(PoolSink<PoolHandle> sink) {
                if (!tryEarmarkForRequest()) {
                    return false;
                }

                BlockHint blockHint = sink.getBlockHint();
                if (blockHint != null && blockHint.blocks(channel.eventLoop())) {
                    sink.tryEmitError(BlockHint.createException());
                    return true;
                }
                if (channel.eventLoop().inEventLoop()) {
                    dispatch0(sink);
                } else {
                    channel.eventLoop().execute(() -> dispatch0(sink));
                }
                return true;
            }

            /**
             * <b>Called on event loop only.</b> Dispatch a stream/connection to the given pool
             * handle request.
             *
             * @param sink The request for a pool handle
             */
            abstract void dispatch0(PoolSink<PoolHandle> sink);

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
            private final AtomicBoolean hasLiveRequest = new AtomicBoolean(false);

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
                return !windDownConnection && hasLiveRequest.compareAndSet(false, true);
            }

            @Override
            boolean hasLiveRequests() {
                return hasLiveRequest.get();
            }

            @Override
            void fireReadTimeout(ChannelHandlerContext ctx) {
                ctx.fireExceptionCaught(ReadTimeoutException.INSTANCE);
            }

            @Override
            void dispatch0(PoolSink<PoolHandle> sink) {
                if (!channel.isActive()) {
                    returnPendingRequest(sink);
                    return;
                }
                PoolHandle ph = new PoolHandle(false, channel) {
                    final ChannelHandlerContext lastContext = channel.pipeline().lastContext();

                    @Override
                    void taint() {
                        windDownConnection = true;
                    }

                    @Override
                    void release() {
                        super.release();
                        if (!windDownConnection) {
                            ChannelHandlerContext newLast = channel.pipeline().lastContext();
                            if (lastContext != newLast) {
                                log.warn("BUG - Handler not removed: {}", newLast);
                                taint();
                            }
                        }
                        if (!windDownConnection) {
                            hasLiveRequest.set(false);
                            markConnectionAvailable();
                        } else {
                            channel.close();
                        }
                    }

                    @Override
                    boolean canReturn() {
                        return !windDownConnection;
                    }

                    @Override
                    void notifyRequestPipelineBuilt() {
                        connectionCustomizer.onRequestPipelineBuilt();
                    }
                };
                emitPoolHandle(sink, ph);
            }

            private void returnPendingRequest(PoolSink<PoolHandle> sink) {
                // failed, but the pending request may still work on another connection.
                addPendingRequest(sink);
                hasLiveRequest.set(false);
            }

            @Override
            void windDownConnection() {
                super.windDownConnection();
                if (!hasLiveRequest.get()) {
                    channel.close();
                }
            }

            @Override
            void onInactive() {
                super.onInactive();
                onConnectionInactive1(this);
            }
        }

        final class Http2ConnectionHolder extends ConnectionHolder {
            private final AtomicInteger liveRequests = new AtomicInteger(0);
            private final Set<Channel> liveStreamChannels = new HashSet<>(); // todo: https://github.com/netty/netty/pull/12830

            Http2ConnectionHolder(Channel channel, NettyClientCustomizer customizer) {
                super(channel, customizer);
            }

            void init() {
                addTimeoutHandlers(
                    requestKey.isSecure() ?
                        ChannelPipelineCustomizer.HANDLER_SSL :
                        ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION
                );

                connectionCustomizer.onStreamPipelineBuilt();

                onNewConnectionEstablished2(this);
            }

            @Override
            boolean tryEarmarkForRequest() {
                return !windDownConnection && incrementWithLimit(liveRequests, configuration.getConnectionPoolConfiguration().getMaxConcurrentRequestsPerHttp2Connection());
            }

            @Override
            boolean hasLiveRequests() {
                return liveRequests.get() > 0;
            }

            @Override
            void fireReadTimeout(ChannelHandlerContext ctx) {
                for (Channel sc : liveStreamChannels) {
                    sc.pipeline().fireExceptionCaught(ReadTimeoutException.INSTANCE);
                }
            }

            @Override
            void dispatch0(PoolSink<PoolHandle> sink) {
                if (!channel.isActive() || windDownConnection) {
                    returnPendingRequest(sink);
                    return;
                }
                addInstrumentedListener(new Http2StreamChannelBootstrap(channel).open(), (Future<Http2StreamChannel> future) -> {
                    if (future.isSuccess()) {
                        Http2StreamChannel streamChannel = future.get();
                        streamChannel.pipeline()
                            .addLast(new Http2StreamFrameToHttpObjectCodec(false))
                            .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_DECOMPRESSOR, new HttpContentDecompressor());
                        NettyClientCustomizer streamCustomizer = connectionCustomizer.specializeForChannel(streamChannel, NettyClientCustomizer.ChannelRole.HTTP2_STREAM);
                        PoolHandle ph = new PoolHandle(true, streamChannel) {
                            @Override
                            void taint() {
                                // do nothing, we don't reuse stream channels
                            }

                            @Override
                            void release() {
                                super.release();
                                liveStreamChannels.remove(streamChannel);
                                streamChannel.close();
                                int newCount = liveRequests.decrementAndGet();
                                if (windDownConnection && newCount <= 0) {
                                    Http2ConnectionHolder.this.channel.close();
                                } else {
                                    markConnectionAvailable();
                                }
                            }

                            @Override
                            boolean canReturn() {
                                return true;
                            }

                            @Override
                            void notifyRequestPipelineBuilt() {
                                streamCustomizer.onRequestPipelineBuilt();
                            }
                        };
                        liveStreamChannels.add(streamChannel);
                        emitPoolHandle(sink, ph);
                    } else {
                        log.debug("Failed to open http2 stream", future.cause());
                        returnPendingRequest(sink);
                    }
                });
            }

            private void returnPendingRequest(PoolSink<PoolHandle> sink) {
                // failed, but the pending request may still work on another connection.
                addPendingRequest(sink);
                liveRequests.decrementAndGet();
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
    }
}
