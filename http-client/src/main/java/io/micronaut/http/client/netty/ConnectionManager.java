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
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.netty.ssl.NettyClientSslBuilder;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.netty.stream.DefaultHttp2Content;
import io.micronaut.http.netty.stream.Http2Content;
import io.micronaut.http.netty.stream.HttpStreamsClientHandler;
import io.micronaut.http.netty.stream.StreamingInboundHttp2ToHttpAdapter;
import io.micronaut.scheduling.instrument.Instrumentation;
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
import io.netty.channel.nio.NioEventLoopGroup;
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
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Connection manager for {@link DefaultHttpClient}. This class manages the lifecycle of netty
 * channels (wrapped in {@link PoolHandle}s), including pooling and timeouts.
 */
@Internal
final class ConnectionManager {
    final ChannelPoolMap<DefaultHttpClient.RequestKey, ChannelPool> poolMap;
    final InvocationInstrumenter instrumenter;
    final HttpVersion httpVersion;

    // not static to avoid build-time initialization by native image
    private final AttributeKey<NettyClientCustomizer> CHANNEL_CUSTOMIZER_KEY =
        AttributeKey.valueOf("micronaut.http.customizer");
    /**
     * Future on a pooled channel that will be completed when the channel has fully connected (e.g.
     * TLS handshake has completed). If unset, then no handshake is needed or it has already
     * completed.
     */
    private final AttributeKey<Future<?>> STREAM_CHANNEL_INITIALIZED =
        AttributeKey.valueOf("micronaut.http.streamChannelInitialized");
    private final AttributeKey<Http2Stream> STREAM_KEY = AttributeKey.valueOf("micronaut.http2.stream");

    private final Logger log;
    private EventLoopGroup group;
    private final boolean shutdownGroup;
    private final ThreadFactory threadFactory;
    private final ChannelFactory<? extends Channel> socketChannelFactory;
    private Bootstrap bootstrap;
    private final HttpClientConfiguration configuration;
    @Nullable
    private final Long readTimeoutMillis;
    @Nullable
    private final Long connectionTimeAliveMillis;
    private final SslContext sslContext;
    private final NettyClientCustomizer clientCustomizer;
    private final Collection<ChannelPipelineListener> pipelineListeners;
    private final String informationalServiceId;

    ConnectionManager(
        Logger log,
        @Nullable EventLoopGroup eventLoopGroup,
        ThreadFactory threadFactory,
        HttpClientConfiguration configuration,
        HttpVersion httpVersion,
        InvocationInstrumenter instrumenter,
        ChannelFactory<? extends Channel> socketChannelFactory,
        NettyClientSslBuilder nettyClientSslBuilder,
        NettyClientCustomizer clientCustomizer,
        Collection<ChannelPipelineListener> pipelineListeners,
        String informationalServiceId) {

        if (httpVersion == null) {
            httpVersion = configuration.getHttpVersion();
        }

        this.log = log;
        this.httpVersion = httpVersion;
        this.threadFactory = threadFactory;
        this.socketChannelFactory = socketChannelFactory;
        this.configuration = configuration;
        this.instrumenter = instrumenter;
        this.clientCustomizer = clientCustomizer;
        this.pipelineListeners = pipelineListeners;
        this.informationalServiceId = informationalServiceId;

        this.connectionTimeAliveMillis = configuration.getConnectTtl()
            .map(duration -> !duration.isNegative() ? duration.toMillis() : null)
            .orElse(null);
        this.readTimeoutMillis = configuration.getReadTimeout()
            .map(duration -> !duration.isNegative() ? duration.toMillis() : null)
            .orElse(null);
        this.sslContext = nettyClientSslBuilder.build(configuration.getSslConfiguration(), httpVersion).orElse(null);

        if (eventLoopGroup != null) {
            group = eventLoopGroup;
            shutdownGroup = false;
        } else {
            group = createEventLoopGroup(configuration, threadFactory);
            shutdownGroup = true;
        }

        initBootstrap();

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

                        AbstractChannelPoolHandler channelPoolHandler = newPoolHandler(key);
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

                        AbstractChannelPoolHandler channelPoolHandler = newPoolHandler(key);
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
        if (poolMap instanceof Iterable) {
            Iterable<Map.Entry<DefaultHttpClient.RequestKey, ChannelPool>> i = (Iterable) poolMap;
            for (Map.Entry<DefaultHttpClient.RequestKey, ChannelPool> entry : i) {
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
     * Get a reactive scheduler that runs on the event loop group of this connection manager.
     *
     * @return A scheduler that runs on the event loop
     */
    public Scheduler getEventLoopScheduler() {
        return Schedulers.fromExecutor(group);
    }

    /**
     * Creates an initial connection to the given remote host.
     *
     * @param requestKey      The request key to connect to
     * @param isStream        Is the connection a stream connection
     * @param isProxy         Is this a streaming proxy
     * @param acceptsEvents   Whether the connection will accept events
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
                throw customizeException(new HttpClientException("Cannot send HTTPS request. SSL is disabled"));
            }
        } else {
            sslCtx = null;
        }
        return sslCtx;
    }

    private PoolHandle mockPoolHandle(Channel channel) {
        return new PoolHandle(null, channel);
    }

    /**
     * Get a connection for exchange-like (non-streaming) http client methods.
     *
     * @param requestKey The remote to connect to
     * @param multipart Whether the request should be multipart
     * @param acceptEvents Whether the response may be an event stream
     * @return A mono that will complete once the channel is ready for transmission
     */
    Mono<PoolHandle> connectForExchange(DefaultHttpClient.RequestKey requestKey, boolean multipart, boolean acceptEvents) {
        return Mono.<PoolHandle>create(emitter -> {
            if (poolMap != null && !multipart) {
                try {
                    ChannelPool channelPool = poolMap.get(requestKey);
                    addInstrumentedListener(channelPool.acquire(), future -> {
                        if (future.isSuccess()) {
                            Channel channel = future.get();
                            PoolHandle poolHandle = new PoolHandle(channelPool, channel);
                            Future<?> initFuture = channel.attr(STREAM_CHANNEL_INITIALIZED).get();
                            emitter.onCancel(poolHandle::release);
                            if (initFuture == null) {
                                emitter.success(poolHandle);
                            } else {
                                // we should wait until the handshake completes
                                addInstrumentedListener(initFuture, f -> {
                                    emitter.success(poolHandle);
                                });
                            }
                        } else {
                            Throwable cause = future.cause();
                            emitter.error(customizeException(new HttpClientException("Connect Error: " + cause.getMessage(), cause)));
                        }
                    });
                } catch (HttpClientException e) {
                    emitter.error(e);
                }
            } else {
                ChannelFuture connectionFuture = doConnect(requestKey, false, false, acceptEvents, null);
                addInstrumentedListener(connectionFuture, future -> {
                    if (!future.isSuccess()) {
                        Throwable cause = future.cause();
                        emitter.error(customizeException(new HttpClientException("Connect Error: " + cause.getMessage(), cause)));
                    } else {
                        PoolHandle ph = mockPoolHandle(connectionFuture.channel());
                        emitter.onCancel(ph::release);
                        emitter.success(ph);
                    }
                });
            }
        })
            .delayUntil(this::delayUntilHttp2Ready)
            .map(poolHandle -> {
                addReadTimeoutHandler(poolHandle.channel.pipeline());
                return poolHandle;
            });
    }

    private Publisher<?> delayUntilHttp2Ready(PoolHandle poolHandle) {
        Http2SettingsHandler settingsHandler = (Http2SettingsHandler) poolHandle.channel.pipeline().get(ChannelPipelineCustomizer.HANDLER_HTTP2_SETTINGS);
        if (settingsHandler == null) {
            return Flux.empty();
        }
        Sinks.Empty<?> empty = Sinks.empty();
        addInstrumentedListener(settingsHandler.promise, future -> {
            if (future.isSuccess()) {
                empty.tryEmitEmpty();
            } else {
                poolHandle.taint();
                poolHandle.release();
                empty.tryEmitError(future.cause());
            }
        });
        return empty.asMono();
    }

    /**
     * Get a connection for streaming http client methods.
     *
     * @param requestKey The remote to connect to
     * @param isProxy Whether the request is for a {@link io.micronaut.http.client.ProxyHttpClient} call
     * @param acceptEvents Whether the response may be an event stream
     * @return A mono that will complete once the channel is ready for transmission
     */
    Mono<PoolHandle> connectForStream(DefaultHttpClient.RequestKey requestKey, boolean isProxy, boolean acceptEvents) {
        return Mono.<PoolHandle>create(emitter -> {
            ChannelFuture channelFuture;
            try {
                if (httpVersion == HttpVersion.HTTP_2_0) {

                    channelFuture = doConnect(requestKey, true, isProxy, acceptEvents, channelHandlerContext -> {
                        try {
                            final Channel channel = channelHandlerContext.channel();
                            emitter.success(mockPoolHandle(channel));
                        } catch (Exception e) {
                            emitter.error(e);
                        }
                    });
                } else {
                    channelFuture = doConnect(requestKey, true, isProxy, acceptEvents, null);
                    addInstrumentedListener(channelFuture,
                        (ChannelFutureListener) f -> {
                            if (f.isSuccess()) {
                                Channel channel = f.channel();
                                emitter.success(mockPoolHandle(channel));
                            } else {
                                Throwable cause = f.cause();
                                emitter.error(customizeException(new HttpClientException("Connect error:" + cause.getMessage(), cause)));
                            }
                        });
                }
            } catch (HttpClientException e) {
                emitter.error(e);
                return;
            }

            // todo: on emitter dispose/cancel, close channel
        })
            .delayUntil(this::delayUntilHttp2Ready)
            .map(poolHandle -> {
                addReadTimeoutHandler(poolHandle.channel.pipeline());
                return poolHandle;
            });
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

        addInstrumentedListener(bootstrap.connect(), future -> {
            if (!future.isSuccess()) {
                initial.tryEmitError(future.cause());
            }
        });

        return initial.asMono();
    }

    private AbstractChannelPoolHandler newPoolHandler(DefaultHttpClient.RequestKey key) {
        return new AbstractChannelPoolHandler() {
            @Override
            public void channelCreated(Channel ch) {
                Promise<?> streamPipelineBuilt = ch.newPromise();
                ch.attr(STREAM_CHANNEL_INITIALIZED).set(streamPipelineBuilt);

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
                        ch.attr(STREAM_CHANNEL_INITIALIZED).set(null);
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
                        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_IDLE_STATE, new IdleStateHandler(idleTimeout.toNanos(), idleTimeout.toNanos(), 0, TimeUnit.NANOSECONDS));
                        pipeline.addLast(IdleTimeoutHandler.INSTANCE);
                    }
                }

                if (ConnectTTLHandler.isChannelExpired(ch) && ch.isOpen() && !ch.eventLoop().isShuttingDown()) {
                    ch.close();
                }

                removeReadTimeoutHandler(pipeline);
            }

            @Override
            public void channelAcquired(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                if (pipeline.context(IdlingConnectionHandler.INSTANCE) != null) {
                    pipeline.remove(IdlingConnectionHandler.INSTANCE);
                }
                if (pipeline.context(ChannelPipelineCustomizer.HANDLER_IDLE_STATE) != null) {
                    pipeline.remove(ChannelPipelineCustomizer.HANDLER_IDLE_STATE);
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
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_SSL, configureSslHandler(sslCtx.newHandler(ch.alloc(), host, port)));
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
                    throw customizeException(new HttpClientException("Unknown Protocol: " + protocol));
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
        pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_UPGRADE_REQUEST, new H2cUpgradeRequestHandler(httpClientInitializer));
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

    <V, C extends Future<V>> Future<V> addInstrumentedListener(
            Future<V> channelFuture, GenericFutureListener<C> listener) {
        return channelFuture.addListener(f -> {
            try (Instrumentation ignored = instrumenter.newInstrumentation()) {
                listener.operationComplete((C) f);
            }
        });
    }

    private <E extends HttpClientException> E customizeException(E exc) {
        DefaultHttpClient.customizeException0(configuration, informationalServiceId, exc);
        return exc;
    }

    private void addReadTimeoutHandler(ChannelPipeline pipeline) {
        if (readTimeoutMillis != null) {
            if (httpVersion == HttpVersion.HTTP_2_0) {
                pipeline.addBefore(
                    ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION,
                    ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT,
                    new ReadTimeoutHandler(readTimeoutMillis, TimeUnit.MILLISECONDS)
                );
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

    private SslHandler configureSslHandler(SslHandler sslHandler) {
        sslHandler.setHandshakeTimeoutMillis(configuration.getSslConfiguration().getHandshakeTimeout().toMillis());
        SSLEngine engine = sslHandler.engine();
        SSLParameters params = engine.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(params);
        return sslHandler;
    }

    /**
     * A handler that triggers the cleartext upgrade to HTTP/2 by sending an initial HTTP request.
     */
    private class H2cUpgradeRequestHandler extends ChannelInboundHandlerAdapter {

        private final HttpClientInitializer initializer;

        /**
         * Default constructor.
         *
         * @param initializer The initializer
         */
        public H2cUpgradeRequestHandler(HttpClientInitializer initializer) {
            this.initializer = initializer;
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
            if (initializer.contextConsumer != null) {
                initializer.contextConsumer.accept(ctx);
            }
            initializer.addFinalHandler(pipeline);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpMessage) {
                int streamId = ((HttpMessage) msg).headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), -1);
                if (streamId == 1) {
                    // ignore this message
                    if (log.isDebugEnabled()) {
                        log.debug("Received response on HTTP2 stream 1, the stream used to respond to the initial upgrade request. Ignoring.");
                    }
                    ReferenceCountUtil.release(msg);
                    if (msg instanceof LastHttpContent) {
                        ctx.pipeline().remove(this);
                    }
                    return;
                }
            }

            super.channelRead(ctx, msg);
        }
    }

    /**
     * Reads the first {@link Http2Settings} object and notifies a {@link io.netty.channel.ChannelPromise}.
     */
    private class Http2SettingsHandler extends
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

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            if (!promise.isDone()) {
                promise.tryFailure(new HttpClientException("Channel became inactive before settings frame was received"));
            }
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            super.handlerRemoved(ctx);
            if (!promise.isDone()) {
                promise.tryFailure(new HttpClientException("Handler was removed before settings frame was received"));
            }
        }
    }

    /**
     * Initializes the HTTP client channel.
     */
    private class HttpClientInitializer extends ChannelInitializer<SocketChannel> {

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
            ch.attr(CHANNEL_CUSTOMIZER_KEY).set(channelCustomizer);

            ChannelPipeline p = ch.pipeline();

            configureProxy(p, sslContext != null, host, port);

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
                        throw customizeException(new HttpClientException("Unsupported log level: " + logLevel));
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
                        throw customizeException(new HttpClientException("Unsupported log level: " + logLevel));
                    }
                });

                if (sslContext != null) {
                    p.addLast(ChannelPipelineCustomizer.HANDLER_SSL, configureSslHandler(sslContext.newHandler(ch.alloc(), host, port)));
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

                p.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_CONTENT, new SimpleChannelInboundHandlerInstrumented<ByteBuf>(instrumenter, false) {

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
            });
        }

        private boolean acceptsEventStream() {
            return this.acceptsEvents;
        }
    }

    final class PoolHandle {
        final Channel channel;
        private final ChannelPool channelPool;
        private boolean canReturn;

        private PoolHandle(ChannelPool channelPool, Channel channel) {
            this.channel = channel;
            this.channelPool = channelPool;
            this.canReturn = channelPool != null;
        }

        /**
         * Prevent this connection from being reused.
         */
        void taint() {
            canReturn = false;
        }

        /**
         * Close this connection or release it back to the pool.
         */
        void release() {
            if (channelPool != null) {
                removeReadTimeoutHandler(channel.pipeline());
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

        /**
         * Whether this connection may be returned to a connection pool (i.e. should be kept
         * keepalive).
         *
         * @return Whether this connection may be reused
         */
        public boolean canReturn() {
            return canReturn;
        }

        /**
         * Notify any {@link NettyClientCustomizer} that the request pipeline has been built.
         */
        void notifyRequestPipelineBuilt() {
            channel.attr(CHANNEL_CUSTOMIZER_KEY).get().onRequestPipelineBuilt();
        }
    }
}
