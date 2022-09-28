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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.netty.ssl.NettyClientSslBuilder;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.channel.ChannelPipelineListener;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.netty.stream.HttpStreamsClientHandler;
import io.micronaut.scheduling.instrument.Instrumentation;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
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
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
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
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connection manager for {@link DefaultHttpClient}. This class manages the lifecycle of netty
 * channels (wrapped in {@link PoolHandle}s), including pooling and timeouts.
 */
@Internal
class ConnectionManager {
    private static final AttributeKey<NettyClientCustomizer> CHANNEL_CUSTOMIZER_KEY =
        AttributeKey.valueOf("micronaut.http.customizer");

    final InvocationInstrumenter instrumenter;
    final HttpVersionSelection httpVersion;

    private final Logger log;
    private final Map<DefaultHttpClient.RequestKey, Pool> pools = new ConcurrentHashMap<>();
    private EventLoopGroup group;
    private final boolean shutdownGroup;
    private final ThreadFactory threadFactory;
    private final Bootstrap bootstrap;
    private final HttpClientConfiguration configuration;
    private final SslContext sslContext;
    private final NettyClientCustomizer clientCustomizer;
    private final Collection<ChannelPipelineListener> pipelineListeners;
    private final String informationalServiceId;

    ConnectionManager(ConnectionManager from) {
        this.instrumenter = from.instrumenter;
        this.httpVersion = from.httpVersion;
        this.log = from.log;
        this.group = from.group;
        this.shutdownGroup = from.shutdownGroup;
        this.threadFactory = from.threadFactory;
        this.bootstrap = from.bootstrap;
        this.configuration = from.configuration;
        this.sslContext = from.sslContext;
        this.clientCustomizer = from.clientCustomizer;
        this.pipelineListeners = from.pipelineListeners;
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
        Collection<ChannelPipelineListener> pipelineListeners,
        String informationalServiceId) {

        if (httpVersion == null) {
            httpVersion = HttpVersionSelection.forClientConfiguration(configuration);
        }

        this.log = log;
        this.httpVersion = httpVersion;
        this.threadFactory = threadFactory;
        this.configuration = configuration;
        this.instrumenter = instrumenter;
        this.clientCustomizer = clientCustomizer;
        this.pipelineListeners = pipelineListeners;
        this.informationalServiceId = informationalServiceId;

        this.sslContext = nettyClientSslBuilder.build(configuration.getSslConfiguration(), httpVersion);

        if (eventLoopGroup != null) {
            group = eventLoopGroup;
            shutdownGroup = false;
        } else {
            group = createEventLoopGroup(configuration, threadFactory);
            shutdownGroup = true;
        }

        this.bootstrap = new Bootstrap();
        this.bootstrap.group(group)
            .channelFactory(socketChannelFactory)
            .option(ChannelOption.SO_KEEPALIVE, true);

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
        group = createEventLoopGroup(configuration, threadFactory);
        bootstrap.group(group);
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

    // for testing
    protected ChannelFuture doConnect(DefaultHttpClient.RequestKey requestKey, ChannelInitializer<?> channelInitializer) {
        String host = requestKey.getHost();
        int port = requestKey.getPort();
        Bootstrap localBootstrap = bootstrap.clone();
        initBootstrapForProxy(localBootstrap, requestKey.isSecure(), host, port);
        localBootstrap.handler(channelInitializer);
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

    /**
     * Get a connection for exchange-like (non-streaming) http client methods.
     *
     * @param requestKey The remote to connect to
     * @param multipart Whether the request should be multipart
     * @param acceptEvents Whether the response may be an event stream
     * @return A mono that will complete once the channel is ready for transmission
     */
    Mono<PoolHandle> connectForExchange(DefaultHttpClient.RequestKey requestKey, boolean multipart, boolean acceptEvents) {
        Pool pool = pools.computeIfAbsent(requestKey, Pool::new);
        return pool.acquire().map(ph -> {
            // TODO: this sucks
            ph.channel.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP_AGGREGATOR, new HttpObjectAggregator(configuration.getMaxContentLength()) {
                @Override
                protected void finishAggregation(FullHttpMessage aggregated) throws Exception {
                    if (!HttpUtil.isContentLengthSet(aggregated)) {
                        if (aggregated.content().readableBytes() > 0) {
                            super.finishAggregation(aggregated);
                        }
                    }
                }
            });
            ph.channel.pipeline().addLast(
                ChannelPipelineCustomizer.HANDLER_HTTP_STREAM,
                new HttpStreamsClientHandler() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt instanceof IdleStateEvent) {
                            // close the connection if it is idle for too long
                            ph.taint();
                            ph.release();
                        }
                        super.userEventTriggered(ctx, evt);
                    }
                }
            );
            return ph;
        });
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
        Pool pool = pools.computeIfAbsent(requestKey, Pool::new);
        return pool.acquire()
            .map(ph -> {
                // TODO: this sucks
                boolean sse = !isProxy && acceptEvents;
                ph.channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    boolean ignoreOneLast = false;

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof HttpResponse &&
                            ((HttpResponse) msg).status().equals(HttpResponseStatus.CONTINUE)) {
                            ignoreOneLast = true;
                        }

                        super.channelRead(ctx, msg);

                        if (msg instanceof LastHttpContent) {
                            if (ignoreOneLast) {
                                ignoreOneLast = false;
                            } else {
                                if (sse) {
                                    ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_EVENT_STREAM);
                                    ctx.pipeline().remove(ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_CONTENT);
                                }
                                ctx.pipeline()
                                    .remove(this)
                                    .remove(ChannelPipelineCustomizer.HANDLER_HTTP_STREAM);
                                ph.release();
                            }
                        }
                    }
                });
                if (sse) {
                    ph.channel.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_EVENT_STREAM, new LineBasedFrameDecoder(configuration.getMaxContentLength(), true, true));
                    ph.channel.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_SSE_CONTENT, new SimpleChannelInboundHandlerInstrumented<ByteBuf>(instrumenter, false) {

                        @Override
                        public boolean acceptInboundMessage(Object msg) {
                            return msg instanceof ByteBuf;
                        }

                        @Override
                        protected void channelReadInstrumented(ChannelHandlerContext ctx, ByteBuf msg) {
                            try {
                                ctx.fireChannelRead(new DefaultHttpContent(msg.copy()));
                            } finally {
                                msg.release();
                            }
                        }
                    });
                }
                ph.channel.pipeline().addLast(
                        ChannelPipelineCustomizer.HANDLER_HTTP_STREAM,
                        new HttpStreamsClientHandler() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                if (evt instanceof IdleStateEvent) {
                                    // close the connection if it is idle for too long
                                    ph.taint();
                                    ph.release();
                                }
                                super.userEventTriggered(ctx, evt);
                            }
                        }
                );
                return ph;
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
        Sinks.Empty<Object> initial = new CancellableMonoSink<>();

        ChannelFuture connectFuture = doConnect(requestKey, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                addLogHandler(ch);

                if (requestKey.isSecure()) {
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

    private Http2FrameCodec makeFrameCodec() {
        Http2FrameCodecBuilder builder = Http2FrameCodecBuilder.forClient();
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
        return builder.build();
    }

    private class AdaptiveAlpnChannelInitializer extends ChannelInitializer<Channel> {
        private final Pool pool;

        final SslContext sslContext;
        final String host;
        final int port;
        private NettyClientCustomizer channelCustomizer;

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
        protected void initChannel(Channel ch) {
            channelCustomizer = clientCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION);
            ch.attr(CHANNEL_CUSTOMIZER_KEY).set(channelCustomizer);

            configureProxy(ch.pipeline(), true, host, port);

            SslHandler sslHandler = sslContext.newHandler(ch.alloc(), host, port);
            sslHandler.setHandshakeTimeoutMillis(configuration.getSslConfiguration().getHandshakeTimeout().toMillis());
            ch.pipeline()
                .addLast(ChannelPipelineCustomizer.HANDLER_SSL, sslHandler)
                .addLast(
                    ChannelPipelineCustomizer.HANDLER_HTTP2_PROTOCOL_NEGOTIATOR,
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

    private void initHttp2(Pool pool, Channel ch, NettyClientCustomizer connectionCustomizer) {
        Http2MultiplexHandler multiplexHandler = new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
            @Override
            protected void initChannel(Http2StreamChannel ch) throws Exception {
                // todo: fail connection?
                log.warn("Server opened HTTP2 stream {}, closing immediately", ch.stream().id());
                ch.close();
            }
        }, new ChannelInitializer<Http2StreamChannel>() {
            @Override
            protected void initChannel(Http2StreamChannel ch) throws Exception {
                // discard any response data for the upgrade request
                ch.close();
            }
        });
        ch.pipeline().addLast(multiplexHandler);
        ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_SETTINGS, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
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
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                // todo: log
                ReferenceCountUtil.release(msg);
                ctx.read();
            }
        });
    }

    private class Http2UpgradeInitializer extends ChannelInitializer<Channel> {
        private final Pool pool;

        Http2UpgradeInitializer(Pool pool) {
            this.pool = pool;
        }

        @Override
        protected void initChannel(Channel ch) throws Exception {
            NettyClientCustomizer connectionCustomizer = clientCustomizer.specializeForChannel(ch, NettyClientCustomizer.ChannelRole.CONNECTION);

            Http2FrameCodec frameCodec = makeFrameCodec();

            HttpClientCodec sourceCodec = new HttpClientCodec();
            Http2ClientUpgradeCodec upgradeCodec = new Http2ClientUpgradeCodec(frameCodec,
                new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, frameCodec);
                        initHttp2(pool, ch, connectionCustomizer);
                    }
                });
            HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536);

            ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC, sourceCodec);
            ch.pipeline().addLast(upgradeHandler);

            ch.pipeline().addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_UPGRADE_REQUEST, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
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

    static abstract class PoolHandle {
        final boolean http2;
        final Channel channel;

        /**
         * Prevent this connection from being reused.
         */
        abstract void taint();

        private PoolHandle(boolean http2, Channel channel) {
            this.http2 = http2;
            this.channel = channel;
        }

        /**
         * Close this connection or release it back to the pool.
         */
        abstract void release();

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
        void notifyRequestPipelineBuilt() {
            channel.attr(CHANNEL_CUSTOMIZER_KEY).get().onRequestPipelineBuilt();
        }
    }

    private final class Pool extends PoolResizer {
        private final DefaultHttpClient.RequestKey requestKey;

        final InitialConnectionErrorHandler initialErrorHandler = new InitialConnectionErrorHandler() {
            @Override
            protected void onNewConnectionFailure(@Nullable Throwable cause) throws Exception {
                Pool.this.onNewConnectionFailure(cause);
            }
        };

        Pool(DefaultHttpClient.RequestKey requestKey) {
            super(log, configuration.getConnectionPoolConfiguration());
            this.requestKey = requestKey;
        }

        Mono<PoolHandle> acquire() {
            Sinks.One<PoolHandle> sink = new CancellableMonoSink<>();
            addPendingRequest(sink);
            Optional<Duration> acquireTimeout = configuration.getConnectionPoolConfiguration().getAcquireTimeout();
            if (acquireTimeout.isPresent()) {
                return sink.asMono().timeout(acquireTimeout.get(), getEventLoopScheduler());
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
        void openNewConnection() {
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
                            protected void initChannel(Channel ch) throws Exception {
                                configureProxy(ch.pipeline(), false, requestKey.getHost(), requestKey.getPort());
                                initHttp1(ch);
                                ch.pipeline().addLast("activity-listener", new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
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
            addInstrumentedListener(doConnect(requestKey, initializer), future -> {
                if (!future.isSuccess()) {
                    onNewConnectionFailure(future.cause());
                }
            });
        }

        public void shutdown() {
            forEachConnection(c -> ((ConnectionHolder) c).channel.close());
        }

        abstract class ConnectionHolder extends ResizerConnection {
            final Channel channel;
            final NettyClientCustomizer connectionCustomizer;
            ScheduledFuture<?> ttlFuture;
            volatile boolean windDownConnection = false;

            ConnectionHolder(Channel channel, NettyClientCustomizer connectionCustomizer) {
                this.channel = channel;
                this.connectionCustomizer = connectionCustomizer;
            }

            final void addTimeoutHandlers(String before) {
                // read timeout handles timeouts *during* a request
                configuration.getReadTimeout()
                    .ifPresent(dur -> channel.pipeline().addBefore(before, ChannelPipelineCustomizer.HANDLER_READ_TIMEOUT, new ReadTimeoutHandler(dur.toNanos(), TimeUnit.NANOSECONDS) {
                        @Override
                        protected void readTimedOut(ChannelHandlerContext ctx) throws Exception {
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
                        protected void readTimedOut(ChannelHandlerContext ctx) throws Exception {
                            if (!hasLiveRequests()) {
                                ctx.close();
                            }
                        }
                    }));
                configuration.getConnectTtl().ifPresent(ttl ->
                    ttlFuture = channel.eventLoop().schedule(this::windDownConnection, ttl.toNanos(), TimeUnit.NANOSECONDS));
                channel.pipeline().addBefore(before, "connection-cleaner", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        super.channelInactive(ctx);
                        onInactive();
                    }
                });
            }

            void windDownConnection() {
                windDownConnection = true;
            }

            @Override
            public boolean dispatch(Sinks.One<PoolHandle> sink) {
                if (!tryEarmarkForRequest()) {
                    return false;
                }

                if (channel.eventLoop().inEventLoop()) {
                    satisfy0(sink);
                } else {
                    channel.eventLoop().execute(() -> satisfy0(sink));
                }
                return true;
            }

            abstract void satisfy0(Sinks.One<PoolHandle> sink);

            abstract boolean tryEarmarkForRequest();

            abstract boolean hasLiveRequests();

            abstract void fireReadTimeout(ChannelHandlerContext ctx);

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
            void satisfy0(Sinks.One<PoolHandle> sink) {
                if (!channel.isActive()) {
                    returnPendingRequest(sink);
                    return;
                }
                Sinks.EmitResult emitResult = sink.tryEmitValue(new PoolHandle(false, channel) {
                    final ChannelHandlerContext lastContext = channel.pipeline().lastContext();

                    @Override
                    void taint() {
                        windDownConnection = true;
                    }

                    @Override
                    void release() {
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
                });
                if (emitResult.isFailure()) {
                    hasLiveRequest.set(false);
                    markConnectionAvailable();
                }
            }

            private void returnPendingRequest(Sinks.One<PoolHandle> sink) {
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
            void satisfy0(Sinks.One<PoolHandle> sink) {
                if (!channel.isActive()) {
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
                                liveStreamChannels.remove(streamChannel);
                                streamChannel.close();
                                liveRequests.decrementAndGet();
                                markConnectionAvailable();
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
                        Sinks.EmitResult emitResult = sink.tryEmitValue(ph);
                        if (emitResult.isFailure()) {
                            ph.release();
                        }
                    } else {
                        log.debug("Failed to open http2 stream", future.cause());
                        returnPendingRequest(sink);
                    }
                });
            }

            private void returnPendingRequest(Sinks.One<PoolHandle> sink) {
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
