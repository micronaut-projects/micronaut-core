package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

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
        localBootstrap.handler(httpClient.new HttpClientInitializer(
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
        bootstrap.handler(httpClient.new HttpClientInitializer(
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

                ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_INIT, httpClient.new HttpClientInitializer(
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
