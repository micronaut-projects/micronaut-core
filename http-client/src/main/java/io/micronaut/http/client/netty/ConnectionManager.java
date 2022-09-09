package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.http.netty.stream.DefaultHttp2Content;
import io.micronaut.http.netty.stream.Http2Content;
import io.micronaut.http.netty.stream.HttpStreamsClientHandler;
import io.micronaut.http.netty.stream.StreamingInboundHttp2ToHttpAdapter;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.Attribute;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.micronaut.http.netty.channel.ChannelPipelineCustomizer.HANDLER_IDLE_STATE;

final class ConnectionManager {
    private final DefaultHttpClient httpClient; // TODO
    private final Logger log;
    EventLoopGroup group;
    final Bootstrap bootstrap;
    final ChannelPoolMap<DefaultHttpClient.RequestKey, ChannelPool> poolMap;
    private final HttpClientConfiguration configuration;
    final InvocationInstrumenter instrumenter;
    @Nullable
    final Long readTimeoutMillis;
    @Nullable
    final Long connectionTimeAliveMillis;
    final HttpVersion httpVersion;
    final SslContext sslContext;
    final NettyClientCustomizer clientCustomizer;
    final Collection<ChannelPipelineListener> pipelineListeners;
    final String informationalServiceId;

    ConnectionManager(
        DefaultHttpClient httpClient,
        Logger log, EventLoopGroup group,
        HttpClientConfiguration configuration,
        HttpVersion httpVersion,
        InvocationInstrumenter instrumenter,
        ChannelFactory<? extends Channel> socketChannelFactory,
        @Nullable Long readTimeoutMillis,
        @Nullable Long connectionTimeAliveMillis,
        SslContext sslContext,
        NettyClientCustomizer clientCustomizer,
        Collection<ChannelPipelineListener> pipelineListeners, String informationalServiceId) {
        this.httpClient = httpClient;
        this.log = log;
        this.httpVersion = httpVersion;
        this.group = group;
        this.sslContext = sslContext;
        this.configuration = configuration;
        this.instrumenter = instrumenter;
        this.readTimeoutMillis = readTimeoutMillis;
        this.connectionTimeAliveMillis = connectionTimeAliveMillis;
        this.clientCustomizer = clientCustomizer;
        this.pipelineListeners = pipelineListeners;
        this.informationalServiceId = informationalServiceId;
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(group)
            .channelFactory(socketChannelFactory)
            .option(ChannelOption.SO_KEEPALIVE, true);

        final ChannelHealthChecker channelHealthChecker = channel -> channel.eventLoop().newSucceededFuture(channel.isActive() && !ConnectTTLHandler.isChannelExpired(channel));

        HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration = configuration.getConnectionPoolConfiguration();
        // HTTP/2 defaults to keep alive connections so should we should always use a pool
        if (connectionPoolConfiguration.isEnabled() || httpVersion == io.micronaut.http.HttpVersion.HTTP_2_0) {
            int maxConnections = connectionPoolConfiguration.getMaxConnections();
            if (maxConnections > -1) {
                poolMap = new AbstractChannelPoolMap<DefaultHttpClient.RequestKey, ChannelPool>() {
                    @Override
                    protected ChannelPool newPool(DefaultHttpClient.RequestKey key) {
                        Bootstrap newBootstrap = bootstrap.clone(group);
                        initBootstrapForProxy(newBootstrap, key.isSecure(), key.getHost(), key.getPort());
                        newBootstrap.remoteAddress(key.getRemoteAddress());

                        AbstractChannelPoolHandler channelPoolHandler = newPoolHandler(key, httpClient);
                        final long acquireTimeoutMillis = connectionPoolConfiguration.getAcquireTimeout().map(Duration::toMillis).orElse(-1L);
                        return new FixedChannelPool(
                            newBootstrap,
                            channelPoolHandler,
                            channelHealthChecker,
                            acquireTimeoutMillis > -1 ? FixedChannelPool.AcquireTimeoutAction.FAIL : null,
                            acquireTimeoutMillis,
                            maxConnections,
                            connectionPoolConfiguration.getMaxPendingAcquires()

                        );
                    }
                };
            } else {
                poolMap = new AbstractChannelPoolMap<DefaultHttpClient.RequestKey, ChannelPool>() {
                    @Override
                    protected ChannelPool newPool(DefaultHttpClient.RequestKey key) {
                        Bootstrap newBootstrap = bootstrap.clone(group);
                        initBootstrapForProxy(newBootstrap, key.isSecure(), key.getHost(), key.getPort());
                        newBootstrap.remoteAddress(key.getRemoteAddress());

                        AbstractChannelPoolHandler channelPoolHandler = newPoolHandler(key, httpClient);
                        return new SimpleChannelPool(
                            newBootstrap,
                            channelPoolHandler,
                            channelHealthChecker
                        );
                    }
                };
            }
        } else {
            this.poolMap = null;
        }
    }

    /**
     * Creates an initial connection to the given remote host.
     *
     * @param requestKey
     * @param isStream        Is the connection a stream connection
     * @param isProxy         Is this a streaming proxy
     * @param acceptsEvents
     * @param contextConsumer The logic to run once the channel is configured correctly
     * @return A ChannelFuture
     * @throws HttpClientException If the URI is invalid
     */
    private ChannelFuture doConnect(
        DefaultHttpClient.RequestKey requestKey,
        boolean isStream,
        boolean isProxy,
        boolean acceptsEvents,
        Consumer<ChannelHandlerContext> contextConsumer) throws HttpClientException {

        SslContext sslCtx = buildSslContext(requestKey);
        String host = requestKey.getHost();
        int port = requestKey.getPort();
        Bootstrap localBootstrap = bootstrap.clone();
        initBootstrapForProxy(localBootstrap, sslCtx != null, host, port);
        localBootstrap.handler(new HttpClientInitializer(
            sslCtx,
            host,
            port,
            isStream,
            isProxy,
            acceptsEvents,
            contextConsumer)
        );
        return localBootstrap.connect(host, port);
    }

    private void initBootstrapForProxy(Bootstrap localBootstrap, boolean sslCtx, String host, int port) {
        Proxy proxy = configuration.resolveProxy(sslCtx, host, port);
        if (proxy.type() != Proxy.Type.DIRECT) {
            localBootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
        }
    }

    static boolean isAcceptEvents(HttpRequest<?> request) {
        String acceptHeader = request.getHeaders().get(io.micronaut.http.HttpHeaders.ACCEPT);
        return acceptHeader != null && acceptHeader.equalsIgnoreCase(MediaType.TEXT_EVENT_STREAM);
    }

    /**
     * Builds an {@link SslContext} for the given URI if necessary.
     *
     * @return The {@link SslContext} instance
     */
    private SslContext buildSslContext(DefaultHttpClient.RequestKey requestKey) {
        final SslContext sslCtx;
        if (requestKey.isSecure()) {
            sslCtx = sslContext;
            //Allow https requests to be sent if SSL is disabled but a proxy is present
            if (sslCtx == null && !configuration.getProxyAddress().isPresent()) {
                throw httpClient.customizeException(new HttpClientException("Cannot send HTTPS request. SSL is disabled"));
            }
        } else {
            sslCtx = null;
        }
        return sslCtx;
    }

    private Future<PoolHandle> acquireChannelFromPool(DefaultHttpClient.RequestKey requestKey) {
        ChannelPool channelPool = poolMap.get(requestKey);
        Future<Channel> channelFuture = channelPool.acquire();
        Promise<PoolHandle> promise = group.next().newPromise();
        channelFuture.addListener(f -> {
            if (channelFuture.isSuccess()) {
                promise.setSuccess(new PoolHandle(channelPool, channelFuture.getNow()));
            } else {
                promise.setFailure(channelFuture.cause());
            }
        });
        return promise;
    }

    private PoolHandle mockPoolHandle(Channel channel) {
        // TODO: delete
        return new PoolHandle(null, channel);
    }

    Mono<PoolHandle> connectForExchange(DefaultHttpClient.RequestKey requestKey, boolean multipart, boolean acceptEvents) {
        return Mono.create(emitter -> {
            if (poolMap != null && !multipart) {
                try {
                    Future<PoolHandle> channelFuture = acquireChannelFromPool(requestKey);
                    httpClient.addInstrumentedListener(channelFuture, future -> {
                        if (future.isSuccess()) {
                            PoolHandle poolHandle = future.get();
                            Channel channel = poolHandle.channel;
                            Future<?> initFuture = channel.attr(DefaultHttpClient.STREAM_CHANNEL_INITIALIZED).get();
                            if (initFuture == null) {
                                emitter.success(poolHandle);
                            } else {
                                // we should wait until the handshake completes
                                httpClient.addInstrumentedListener(initFuture, f -> {
                                    emitter.success(poolHandle);
                                });
                            }
                        } else {
                            Throwable cause = future.cause();
                            emitter.error(httpClient.customizeException(new HttpClientException("Connect Error: " + cause.getMessage(), cause)));
                        }
                    });
                } catch (HttpClientException e) {
                    emitter.error(e);
                }
            } else {
                ChannelFuture connectionFuture = doConnect(requestKey, false, false, acceptEvents, null);
                httpClient.addInstrumentedListener(connectionFuture, future -> {
                    if (!future.isSuccess()) {
                        Throwable cause = future.cause();
                        emitter.error(httpClient.customizeException(new HttpClientException("Connect Error: " + cause.getMessage(), cause)));
                    } else {
                        emitter.success(mockPoolHandle(connectionFuture.channel()));
                    }
                });
            }
        });
    }

    Mono<Channel> connectForStream(DefaultHttpClient.RequestKey requestKey, boolean isProxy, boolean acceptEvents) {
        return Mono.create(emitter -> {
            ChannelFuture channelFuture;
            try {
                if (httpVersion == HttpVersion.HTTP_2_0) {

                    channelFuture = doConnect(requestKey, true, isProxy, acceptEvents, channelHandlerContext -> {
                        try {
                            final Channel channel = channelHandlerContext.channel();
                            emitter.success(channel);
                        } catch (Exception e) {
                            emitter.error(e);
                        }
                    });
                } else {
                    channelFuture = doConnect(requestKey, true, isProxy, acceptEvents, null);
                    httpClient.addInstrumentedListener(channelFuture,
                        (ChannelFutureListener) f -> {
                            if (f.isSuccess()) {
                                Channel channel = f.channel();
                                emitter.success(channel);
                            } else {
                                Throwable cause = f.cause();
                                emitter.error(httpClient.customizeException(new HttpClientException("Connect error:" + cause.getMessage(), cause)));
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
        });
    }

    Mono<?> connectForWebsocket(DefaultHttpClient.RequestKey requestKey, ChannelHandler handler) {
        Sinks.Empty<Object> initial = Sinks.empty();

        Bootstrap bootstrap = this.bootstrap.clone();
        SslContext sslContext = buildSslContext(requestKey);

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

                try {
                    pipeline.addLast(WebSocketClientCompressionHandler.INSTANCE);
                    pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_WEBSOCKET_CLIENT, handler);
                    initial.tryEmitEmpty();
                } catch (Throwable e) {
                    initial.tryEmitError(new WebSocketSessionException("Error opening WebSocket client session: " + e.getMessage(), e));
                }
            }
        });

        httpClient.addInstrumentedListener(bootstrap.connect(), future -> {
            if (!future.isSuccess()) {
                initial.tryEmitError(future.cause());
            }
        });

        return initial.asMono();
    }

    private Disposable buildDisposableChannel(ChannelFuture channelFuture) {
        return new Disposable() {
            private final AtomicBoolean disposed = new AtomicBoolean(false);

            @Override
            public void dispose() {
                if (disposed.compareAndSet(false, true)) {
                    Channel channel = channelFuture.channel();
                    if (channel.isOpen()) {
                        httpClient.closeChannelAsync(channel);
                    }
                }
            }

            @Override
            public boolean isDisposed() {
                return disposed.get();
            }
        };
    }

    private AbstractChannelPoolHandler newPoolHandler(DefaultHttpClient.RequestKey key, DefaultHttpClient httpClient) {
        return new AbstractChannelPoolHandler() {
            @Override
            public void channelCreated(Channel ch) {
                Promise<?> streamPipelineBuilt = ch.newPromise();
                ch.attr(DefaultHttpClient.STREAM_CHANNEL_INITIALIZED).set(streamPipelineBuilt);

                // make sure the future completes eventually
                ChannelHandler failureHandler = new ChannelInboundHandlerAdapter() {
                    @Override
                    public void handlerRemoved(ChannelHandlerContext ctx) {
                        streamPipelineBuilt.trySuccess(null);
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                        streamPipelineBuilt.trySuccess(null);
                        ctx.fireChannelInactive();
                    }
                };
                ch.pipeline().addLast(failureHandler);

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

                    @Override
                    void onStreamPipelineBuilt() {
                        super.onStreamPipelineBuilt();
                        streamPipelineBuilt.trySuccess(null);
                        ch.pipeline().remove(failureHandler);
                        ch.attr(DefaultHttpClient.STREAM_CHANNEL_INITIALIZED).set(null);
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

                if (ConnectTTLHandler.isChannelExpired(ch) && ch.isOpen() && !ch.eventLoop().isShuttingDown()) {
                    ch.close();
                }

                httpClient.removeReadTimeoutHandler(pipeline);
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
     * Configures HTTP/2 for the channel when SSL is enabled.
     *
     * @param httpClientInitializer The client initializer
     * @param ch                    The channel
     * @param sslCtx                The SSL context
     * @param host                  The host
     * @param port                  The port
     * @param connectionHandler     The connection handler
     */
    private void configureHttp2Ssl(
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
                    throw httpClient.customizeException(new HttpClientException("Unknown Protocol: " + protocol));
                }
                httpClientInitializer.onStreamPipelineBuilt();
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
    private void configureHttp2ClearText(
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
        pipeline.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                ctx.fireUserEventTriggered(evt);
                if (evt instanceof HttpClientUpgradeHandler.UpgradeEvent) {
                    httpClientInitializer.onStreamPipelineBuilt();
                    ctx.pipeline().remove(this);
                }
            }
        });
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
    @NonNull
    private static HttpToHttp2ConnectionHandlerBuilder newHttp2ConnectionHandlerBuilder(
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

    /**
     * A handler that triggers the cleartext upgrade to HTTP/2 by sending an initial HTTP request.
     */
    static class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {

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
                    new DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER);

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
     * Reads the first {@link Http2Settings} object and notifies a {@link io.netty.channel.ChannelPromise}.
     */
    class Http2SettingsHandler extends
            SimpleChannelInboundHandlerInstrumented<Http2Settings> {
        final ChannelPromise promise;

        /**
         * Create new instance.
         *
         * @param promise Promise object used to notify when first settings are received
         */
        Http2SettingsHandler(ChannelPromise promise) {
            super(instrumenter);
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
        final Consumer<ChannelHandlerContext> contextConsumer;
        private NettyClientCustomizer channelCustomizer;

        /**
         * @param sslContext      The ssl context
         * @param host            The host
         * @param port            The port
         * @param stream          Whether is stream
         * @param proxy           Is this a streaming proxy
         * @param acceptsEvents   Whether an event stream is accepted
         * @param contextConsumer The context consumer
         */
        protected HttpClientInitializer(SslContext sslContext,
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
            channelCustomizer = clientCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION);
            ch.attr(DefaultHttpClient.CHANNEL_CUSTOMIZER_KEY).set(channelCustomizer);

            ChannelPipeline p = ch.pipeline();

            Proxy proxy = configuration.resolveProxy(sslContext != null, host, port);
            if (!Proxy.NO_PROXY.equals(proxy)) {
                httpClient.configureProxy(p, proxy);
            }

            if (httpVersion == HttpVersion.HTTP_2_0) {
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
                        throw httpClient.customizeException(new HttpClientException("Unsupported log level: " + logLevel));
                    }
                });
                HttpToHttp2ConnectionHandler connectionHandler = builder
                        .build();
                if (sslContext != null) {
                    configureHttp2Ssl(this, ch, sslContext, host, port, connectionHandler);
                } else {
                    configureHttp2ClearText(this, ch, connectionHandler);
                }
                channelCustomizer.onInitialPipelineBuilt();
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
                        throw httpClient.customizeException(new HttpClientException("Unsupported log level: " + logLevel));
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
                                p.addLast(HANDLER_IDLE_STATE, new IdleStateHandler(
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
                channelCustomizer.onInitialPipelineBuilt();
                onStreamPipelineBuilt();
            }
        }

        /**
         * Called when the stream pipeline is fully set up (all handshakes completed) and we can
         * start processing requests.
         */
        void onStreamPipelineBuilt() {
            channelCustomizer.onStreamPipelineBuilt();
        }

        void addHttp1Handlers(ChannelPipeline p) {
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

        void addEventStreamHandlerIfNecessary(ChannelPipeline p) {
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
                                Attribute<Http2Stream> streamKey = ctx.channel().attr(DefaultHttpClient.STREAM_KEY);
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

                p.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_CONTENT, new SimpleChannelInboundHandlerInstrumented<ByteBuf>(httpClient.connectionManager.instrumenter, false) {

                    @Override
                    public boolean acceptInboundMessage(Object msg) {
                        return msg instanceof ByteBuf;
                    }

                    @Override
                    protected void channelReadInstrumented(ChannelHandlerContext ctx, ByteBuf msg) {
                        try {
                            Attribute<Http2Stream> streamKey = ctx.channel().attr(DefaultHttpClient.STREAM_KEY);
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
                    return super.isValidInMessage(msg) && (sslContext != null || !httpClient.discardH2cStream((HttpMessage) msg));
                }
            });
        }

        private boolean acceptsEventStream() {
            return this.acceptsEvents;
        }
    }

    class PoolHandle {
        final Channel channel;
        private final ChannelPool channelPool;
        private boolean canReturn;

        private PoolHandle(ChannelPool channelPool, Channel channel) {
            this.channel = channel;
            this.channelPool = channelPool;
            this.canReturn = channelPool != null;
        }

        void taint() {
            canReturn = false;
        }

        void release() {
            if (channelPool != null) {
                httpClient.removeReadTimeoutHandler(channel.pipeline());
                if (!canReturn) {
                    channel.closeFuture().addListener((future ->
                        channelPool.release(channel)
                    ));
                } else {
                    channelPool.release(channel);
                }
            } else {
                // just close it to prevent any future reads without a handler registered
                channel.close();
            }
        }
    }
}
