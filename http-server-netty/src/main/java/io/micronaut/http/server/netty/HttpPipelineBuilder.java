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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.handler.Http2ServerHandler;
import io.micronaut.http.server.netty.handler.PipeliningServerHandler;
import io.micronaut.http.server.netty.handler.RequestHandler;
import io.micronaut.http.server.netty.handler.accesslog.HttpAccessLogHandler;
import io.micronaut.http.server.netty.websocket.NettyServerWebSocketUpgradeHandler;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.runtime.server.GracefulShutdownCapable;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.pcap.PcapWriteHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.incubator.codec.http3.DefaultHttp3GoAwayFrame;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3FrameToHttpObjectCodec;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslEngine;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AsciiString;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.cert.Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Helper class that manages the {@link ChannelPipeline} of incoming HTTP connections.
 *
 * This outer class is basically a singleton, though it may be replaced when the configuration is reloaded. Inner
 * classes are connection-scoped.
 *
 * @since 3.4
 * @author ywkat
 */
final class HttpPipelineBuilder implements Closeable {
    static final Supplier<AttributeKey<StreamPipeline>> STREAM_PIPELINE_ATTRIBUTE =
        SupplierUtil.memoized(() -> AttributeKey.newInstance("stream-pipeline"));
    static final Supplier<AttributeKey<Supplier<Certificate>>> CERTIFICATE_SUPPLIER_ATTRIBUTE =
        SupplierUtil.memoized(() -> AttributeKey.newInstance("certificate-supplier"));

    private static final Logger LOG = LoggerFactory.getLogger(HttpPipelineBuilder.class);

    private final NettyHttpServer server;
    private final NettyEmbeddedServices embeddedServices;
    private final ServerSslConfiguration sslConfiguration;
    private final RoutingInBoundHandler routingInBoundHandler;
    private final HttpHostResolver hostResolver;

    private final LoggingHandler loggingHandler;
    private final SslContext sslContext;
    private final QuicSslContext quicSslContext;
    private final HttpAccessLogHandler accessLogHandler;

    private final NettyServerCustomizer serverCustomizer;

    private final boolean quic;

    HttpPipelineBuilder(NettyHttpServer server, NettyEmbeddedServices embeddedServices, ServerSslConfiguration sslConfiguration, RoutingInBoundHandler routingInBoundHandler, HttpHostResolver hostResolver, NettyServerCustomizer serverCustomizer, boolean quic) {
        this.server = server;
        this.embeddedServices = embeddedServices;
        this.sslConfiguration = sslConfiguration;
        this.routingInBoundHandler = routingInBoundHandler;
        this.hostResolver = hostResolver;
        this.serverCustomizer = serverCustomizer;
        this.quic = quic;

        Optional<LogLevel> logLevel = server.getServerConfiguration().getLogLevel();
        loggingHandler = logLevel.map(level -> new LoggingHandler(NettyHttpServer.class, level)).orElse(null);
        sslContext = embeddedServices.getServerSslBuilder() != null && !quic ? embeddedServices.getServerSslBuilder().build().orElse(null) : null;
        quicSslContext = quic ? embeddedServices.getServerSslBuilder().buildQuic().orElse(null) : null;

        NettyHttpServerConfiguration.AccessLogger accessLogger = server.getServerConfiguration().getAccessLogger();
        // todo: http2serverhandler support
        if (accessLogger != null && accessLogger.isEnabled()) {
            accessLogHandler = new HttpAccessLogHandler(accessLogger.getLoggerName(), accessLogger.getLogFormat(), NettyHttpServer.inclusionPredicate(accessLogger));
            routingInBoundHandler.supportLoggingHandler = true;
        } else {
            accessLogHandler = null;
        }
    }

    boolean supportsSsl() {
        return sslContext != null;
    }

    @Override
    public void close() {
        ReferenceCountUtil.release(sslContext);
    }

    private RequestHandler makeRequestHandler(Optional<NettyServerWebSocketUpgradeHandler> webSocketUpgradeHandler, boolean ssl) {
        RequestHandler requestHandler = routingInBoundHandler;
        if (webSocketUpgradeHandler.isPresent()) {
            webSocketUpgradeHandler.get().setNext(routingInBoundHandler);
            requestHandler = webSocketUpgradeHandler.get();
        }
        if (server.getServerConfiguration().isDualProtocol() && server.getServerConfiguration().isHttpToHttpsRedirect() && !ssl) {
            requestHandler = new HttpToHttpsRedirectHandler(routingInBoundHandler.conversionService, server.getServerConfiguration(), sslConfiguration, hostResolver);
        }
        return requestHandler;
    }

    static String toString(@Nullable SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            return inet.getHostString() + ":" + inet.getPort();
        } else {
            return "unknown";
        }
    }

    /**
     * Holder class for normal or QUIC ssl handler.
     */
    private final class SslHandlerHolder {
        private SslHandler sslHandler;
        private SSLEngine quicSslEngine;

        /**
         * Make a normal (non-quic) ssl handler. Note that this may be reference counted.
         *
         * @param alloc The channel allocator
         * @return The handler
         */
        SslHandler makeNormal(ByteBufAllocator alloc) {
            sslHandler = sslContext.newHandler(alloc);
            sslHandler.setHandshakeTimeoutMillis(sslConfiguration.getHandshakeTimeout().toMillis());
            return sslHandler;
        }

        /**
         * Create a supplier that looks up the peer cert of this connection ({@link #CERTIFICATE_SUPPLIER_ATTRIBUTE}).
         *
         * @return The supplier
         */
        Supplier<Certificate> findPeerCert() {
            return SupplierUtil.memoized(() -> {
                try {
                    return (quicSslEngine == null ? sslHandler.engine() : quicSslEngine).getSession().getPeerCertificates()[0];
                } catch (SSLPeerUnverifiedException ex) {
                    return null;
                }
            });
        }

        HttpPipelineBuilder pipelineBuilder() {
            return HttpPipelineBuilder.this;
        }
    }

    /**
     * Factory class for {@link QuicSslEngine}, this is a nested class for compatibility when QUIC
     * isn't on the classpath.
     */
    private static final class QuicFactory {
        /**
         * Create a QUIC SSL engine provider ({@link io.netty.incubator.codec.quic.QuicCodecBuilder#sslEngineProvider(Function)}).
         *
         * @return The engine provider
         */
        static Function<QuicChannel, ? extends QuicSslEngine> quicEngineFactory(SslHandlerHolder sslHandlerHolder) {
            return q -> {
                @SuppressWarnings("resource")
                QuicSslEngine e = sslHandlerHolder.pipelineBuilder().quicSslContext.newEngine(q.alloc());
                sslHandlerHolder.quicSslEngine = e;
                return e;
            };
        }
    }

    final class ConnectionPipeline implements GracefulShutdownCapable {
        final Channel channel;
        private final ChannelPipeline pipeline;

        @Nullable
        private final SslHandlerHolder sslHandler;

        private final NettyServerCustomizer connectionCustomizer;

        private volatile GracefulShutdownCapable specificGracefulShutdown;

        /**
         * @param channel The channel of this connection
         * @param https   Whether this connection is HTTPS
         */
        ConnectionPipeline(Channel channel, boolean https) {
            this.channel = channel;
            this.pipeline = channel.pipeline();
            this.sslHandler = https ? new SslHandlerHolder() : null;
            this.connectionCustomizer = serverCustomizer.specializeForChannel(channel, NettyServerCustomizer.ChannelRole.CONNECTION);
        }

        void insertPcapLoggingHandler(Channel ch, String qualifier) {
            String pattern = server.getServerConfiguration().getPcapLoggingPathPattern();
            if (pattern == null) {
                return;
            }

            String path = pattern;
            path = path.replace("{qualifier}", qualifier);
            if (ch.localAddress() != null) {
                path = path.replace("{localAddress}", resolveIfNecessary(ch.localAddress()));
            }
            if (ch.remoteAddress() != null) {
                path = path.replace("{remoteAddress}", resolveIfNecessary(ch.remoteAddress()));
            }
            if (quic && ch instanceof QuicStreamChannel qsc) {
                path = path.replace("{localAddress}", resolveIfNecessary(qsc.parent().localSocketAddress()));
                path = path.replace("{remoteAddress}", resolveIfNecessary(qsc.parent().remoteSocketAddress()));
            }
            path = path.replace("{random}", Long.toHexString(ThreadLocalRandom.current().nextLong()));
            path = path.replace("{timestamp}", Instant.now().toString());

            path = path.replace(':', '_'); // for windows

            LOG.warn("Logging *full* request data, as configured. This will contain sensitive information! Path: '{}'", path);

            try {
                PcapWriteHandler.Builder builder = PcapWriteHandler.builder();

                if (quic && ch instanceof QuicStreamChannel qsc) {
                    builder.forceTcpChannel((InetSocketAddress) qsc.parent().localSocketAddress(), (InetSocketAddress) qsc.parent().remoteSocketAddress(), true);
                }

                ch.pipeline().addLast(builder.build(new FileOutputStream(path)));
            } catch (FileNotFoundException e) {
                LOG.warn("Failed to create target pcap at '{}', not logging.", path, e);
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
                    address = new InetSocketAddress(socketAddress.getHostString(), socketAddress.getPort());
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

        void initChannel() {
            insertOuterTcpHandlers();

            if (server.getServerConfiguration().getHttpVersion() != io.micronaut.http.HttpVersion.HTTP_2_0) {
                configureForHttp1();
            } else {
                if (sslHandler != null) {
                    configureForAlpn();
                } else {
                    configureForH2cSupport();
                }
            }
        }

        void initHttp3Channel() {
            insertPcapLoggingHandler(channel, "udp-encapsulated");

            Set<Http3GracefulShutdown> activeChannels = ConcurrentHashMap.newKeySet();
            AtomicBoolean shuttingDown = new AtomicBoolean(false);
            pipeline.addLast(Http3.newQuicServerCodecBuilder()
                .sslEngineProvider(QuicFactory.quicEngineFactory(sslHandler))
                    //.sslEngineProvider(q -> quicSslContext.newEngine(q.alloc()))
                .initialMaxData(server.getServerConfiguration().getHttp3().getInitialMaxData())
                .initialMaxStreamDataBidirectionalLocal(server.getServerConfiguration().getHttp3().getInitialMaxStreamDataBidirectionalLocal())
                .initialMaxStreamDataBidirectionalRemote(server.getServerConfiguration().getHttp3().getInitialMaxStreamDataBidirectionalRemote())
                .initialMaxStreamsBidirectional(server.getServerConfiguration().getHttp3().getInitialMaxStreamsBidirectional())
                .tokenHandler(QuicTokenHandlerImpl.create(channel.alloc()))
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel ch) throws Exception {
                        if (shuttingDown.get()) {
                            ch.close();
                            return;
                        }

                        AtomicLong maxStreamId = new AtomicLong();
                        insertPcapLoggingHandler(ch, "quic-decapsulated");
                        Http3ServerConnectionHandler connectionHandler = new Http3ServerConnectionHandler(new ChannelInitializer<QuicStreamChannel>() {
                            @Override
                            protected void initChannel(@NonNull QuicStreamChannel ch) throws Exception {
                                while (true) {
                                    long m = maxStreamId.get();
                                    if (m >= ch.streamId() || maxStreamId.compareAndSet(m, ch.streamId())) {
                                        break;
                                    }
                                }
                                StreamPipeline streamPipeline = new StreamPipeline(ch, sslHandler, connectionCustomizer.specializeForChannel(ch, NettyServerCustomizer.ChannelRole.REQUEST_STREAM));
                                streamPipeline.insertHttp3FrameHandlers();
                                streamPipeline.streamCustomizer.onStreamPipelineBuilt();
                            }
                        }, new ChannelInitializer<QuicStreamChannel>() {
                            @Override
                            protected void initChannel(@NonNull QuicStreamChannel ch) throws Exception {
                            }
                        }, null, null, true);

                        ch.pipeline().addLast(connectionHandler);
                        Http3GracefulShutdown gracefulShutdown = new Http3GracefulShutdown(ch.pipeline().lastContext(), maxStreamId);
                        activeChannels.add(gracefulShutdown);
                        ch.closeFuture().addListener((ChannelFutureListener) future -> activeChannels.remove(gracefulShutdown));
                    }
                })
                .build());
            specificGracefulShutdown = new GracefulShutdownCapable() {
                @Override
                public @NonNull CompletionStage<?> shutdownGracefully() {
                    shuttingDown.set(true);
                    return GracefulShutdownCapable.shutdownAll(activeChannels.stream());
                }

                @Override
                public @NonNull Optional<ShutdownState> reportShutdownState() {
                    return CombinedShutdownState.combineShutdownState(activeChannels, Http3GracefulShutdown::key, n -> Map.entry("other", new SingleShutdownState("And " + n + " other connections")));
                }
            };
        }

        /**
         * Insert handlers that wrap the outermost TCP stream. This is SSL and potentially packet capture.
         */
        void insertOuterTcpHandlers() {
            insertPcapLoggingHandler(channel, "encapsulated");

            if (sslHandler != null) {
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_SSL, sslHandler.makeNormal(channel.alloc()));

                insertPcapLoggingHandler(channel, "ssl-decapsulated");
            }

            if (loggingHandler != null) {
                pipeline.addLast(loggingHandler);
            }
        }

        private void onRequestPipelineBuilt() {
            server.triggerPipelineListeners(pipeline);
        }

        /**
         * Insert the handler that produces {@link io.netty.handler.timeout.IdleStateEvent}s when there hasn't been new
         * data for a while.
         */
        private void insertIdleStateHandler() {
            final Duration idleTime = server.getServerConfiguration().getIdleTimeout();
            if (!idleTime.isNegative()) {
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_IDLE_STATE, new IdleStateHandler(
                        (int) server.getServerConfiguration().getReadIdleTimeout().getSeconds(),
                        (int) server.getServerConfiguration().getWriteIdleTimeout().getSeconds(),
                        (int) idleTime.getSeconds()));
            }
        }

        /**
         * Configure this pipeline for normal HTTP 1.
         */
        void configureForHttp1() {
            insertIdleStateHandler();

            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_SERVER_CODEC, createServerCodec());

            specificGracefulShutdown = new StreamPipeline(channel, sslHandler, connectionCustomizer).insertHttp1DownstreamHandlers();

            connectionCustomizer.onInitialPipelineBuilt();
            connectionCustomizer.onStreamPipelineBuilt();
            onRequestPipelineBuilt();
        }

        /**
         * Insert the handlers for normal HTTP 2, after ALPN.
         */
        private void configureForHttp2() {
            insertIdleStateHandler();

            boolean legacyMultiplexHandlers = routingInBoundHandler.serverConfiguration.isLegacyMultiplexHandlers();
            if (legacyMultiplexHandlers) {
                Http2FrameCodec http2FrameCodec = createHttp2FrameCodec();
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, http2FrameCodec);
                specificGracefulShutdown = new Http2GracefulShutdown(pipeline.lastContext(), http2FrameCodec);
                pipeline.addLast(makeHttp2Handler());
            } else {
                Http2ConnectionHandler http2ServerHandler = createHttp2ServerHandler(true);
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, http2ServerHandler);
                specificGracefulShutdown = new Http2GracefulShutdown(pipeline.lastContext(), http2ServerHandler);
            }

            connectionCustomizer.onInitialPipelineBuilt();
            onRequestPipelineBuilt();

            if (!legacyMultiplexHandlers) {
                new StreamPipeline(channel, sslHandler, connectionCustomizer).afterHttp2ServerHandlerSetUp();
            }
        }

        private Http2FrameCodec createHttp2FrameCodec() {
            Http2FrameCodecBuilder builder = Http2FrameCodecBuilder.forServer()
                    .validateHeaders(server.getServerConfiguration().isValidateHeaders())
                    .initialSettings(server.getServerConfiguration().getHttp2().http2Settings());
            server.getServerConfiguration().getLogLevel().ifPresent(logLevel ->
                    builder.frameLogger(new Http2FrameLogger(logLevel, NettyHttpServer.class)));
            return builder.build();
        }

        private Http2ConnectionHandler createHttp2ServerHandler(boolean ssl) {
            Http2ServerHandler.ConnectionHandlerBuilder builder = new Http2ServerHandler.ConnectionHandlerBuilder(makeRequestHandler(embeddedServices.getWebSocketUpgradeHandler(server), ssl))
                .compressor(embeddedServices.getHttpCompressionStrategy())
                .validateHeaders(server.getServerConfiguration().isValidateHeaders())
                .initialSettings(server.getServerConfiguration().getHttp2().http2Settings());
            server.getServerConfiguration().getLogLevel().ifPresent(logLevel ->
                builder.frameLogger(new Http2FrameLogger(logLevel, NettyHttpServer.class)));
            return builder.build();
        }

        /**
         * Configure this pipeline for ALPN.
         */
        void configureForAlpn() {
            pipeline.addLast(new ApplicationProtocolNegotiationHandler(server.getServerConfiguration().getFallbackProtocol()) {
                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    if (routingInBoundHandler.isIgnorable(cause)) {
                        // just abandon ship, nothing can be done here to recover
                        ctx.close();
                    } else {
                        super.exceptionCaught(ctx, cause);
                    }
                }

                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    if (evt instanceof SslHandshakeCompletionEvent event) {
                        if (!event.isSuccess()) {
                            final Throwable cause = event.cause();
                            if (!(cause instanceof ClosedChannelException)) {
                                super.userEventTriggered(ctx, evt);
                            } else {
                                return;
                            }
                        }
                    }
                    super.userEventTriggered(ctx, evt);
                }

                @Override
                protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                    switch (protocol) {
                        case ApplicationProtocolNames.HTTP_2:
                            configureForHttp2();
                            break;
                        case ApplicationProtocolNames.HTTP_1_1:
                            configureForHttp1();
                            break;
                        default:
                            LOG.warn("Negotiated unknown ALPN protocol. Is the fallback protocol configured correctly? Falling back on HTTP 1");
                            configureForHttp1();
                            break;
                    }
                    ctx.read();
                }
            });
        }

        /**
         * Configure this pipeline for plaintext HTTP, with the possibility to upgrade to h2c.
         */
        void configureForH2cSupport() {
            insertIdleStateHandler();

            // todo: move to Http2ServerHandler (upgrade request is a bit difficult)

            final Http2FrameCodec frameCodec;
            final Http2ConnectionHandler connectionHandler;
            if (server.getServerConfiguration().isLegacyMultiplexHandlers()) {
                frameCodec = createHttp2FrameCodec();
                connectionHandler = frameCodec;
            } else {
                connectionHandler = createHttp2ServerHandler(false);
                frameCodec = null;
            }
            final String fallbackHandlerName = "http1-fallback-handler";
            HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory = protocol -> {
                if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                    class Http2ServerUpgradeCodecImpl extends Http2ServerUpgradeCodec {
                        public Http2ServerUpgradeCodecImpl(Http2ConnectionHandler connectionHandler) {
                            super(connectionHandler);
                        }

                        public Http2ServerUpgradeCodecImpl(Http2FrameCodec http2Codec, ChannelHandler... handlers) {
                            super(http2Codec, handlers);
                        }

                        @Override
                        public void upgradeTo(ChannelHandlerContext ctx, FullHttpRequest upgradeRequest) {
                            super.upgradeTo(ctx, upgradeRequest);
                            pipeline.remove(fallbackHandlerName);
                            new StreamPipeline(channel, sslHandler, connectionCustomizer).afterHttp2ServerHandlerSetUp();
                            specificGracefulShutdown = new Http2GracefulShutdown(ctx.pipeline().context(connectionHandler), connectionHandler);
                            onRequestPipelineBuilt();
                        }
                    }

                    if (frameCodec == null) {
                        return new Http2ServerUpgradeCodecImpl(connectionHandler);
                    } else {
                        return new Http2ServerUpgradeCodecImpl(frameCodec, new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                            @Override
                            protected void initChannel(@NonNull Http2StreamChannel ch) {
                                StreamPipeline streamPipeline = new StreamPipeline(ch, sslHandler, connectionCustomizer.specializeForChannel(ch, NettyServerCustomizer.ChannelRole.REQUEST_STREAM));
                                streamPipeline.insertHttp2FrameHandlers();
                                streamPipeline.streamCustomizer.onStreamPipelineBuilt();
                            }
                        }));
                    }
                } else {
                    return null;
                }
            };

            final HttpServerCodec sourceCodec = createServerCodec();
            final HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(
                    sourceCodec,
                    upgradeCodecFactory,
                    server.getServerConfiguration().getMaxH2cUpgradeRequestSize()
            );
            final CleartextHttp2ServerUpgradeHandler cleartextHttp2ServerUpgradeHandler =
                    new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, connectionHandler);

            pipeline.addLast(cleartextHttp2ServerUpgradeHandler);
            pipeline.addLast(fallbackHandlerName, new SimpleChannelInboundHandler<HttpMessage>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, HttpMessage msg) {
                    // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
                    ChannelPipeline cp = ctx.pipeline();

                    // remove the handlers we don't need anymore
                    cp.remove(upgradeHandler);
                    cp.remove(this);

                    // reconfigure for http1
                    // note: we have to reuse the serverCodec in case it still has some data buffered
                    specificGracefulShutdown = new StreamPipeline(channel, sslHandler, connectionCustomizer).insertHttp1DownstreamHandlers();
                    connectionCustomizer.onStreamPipelineBuilt();
                    onRequestPipelineBuilt();
                    cp.fireChannelRead(ReferenceCountUtil.retain(msg));
                }
            });
            connectionCustomizer.onInitialPipelineBuilt();
        }

        @NonNull
        private Http2MultiplexHandler makeHttp2Handler() {
            return new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(@NonNull Channel ch) {
                    StreamPipeline streamPipeline = new StreamPipeline(ch, sslHandler, connectionCustomizer.specializeForChannel(ch, NettyServerCustomizer.ChannelRole.REQUEST_STREAM));
                    streamPipeline.insertHttp2FrameHandlers();
                    streamPipeline.streamCustomizer.onStreamPipelineBuilt();
                }
            });
        }

        @NonNull
        private HttpServerCodec createServerCodec() {
            return new HttpServerCodec(
                    server.getServerConfiguration().getMaxInitialLineLength(),
                    server.getServerConfiguration().getMaxHeaderSize(),
                    server.getServerConfiguration().getMaxChunkSize(),
                    server.getServerConfiguration().isValidateHeaders(),
                    server.getServerConfiguration().getInitialBufferSize()
            );
        }

        @Override
        public CompletionStage<?> shutdownGracefully() {
            GracefulShutdownCapable specificGracefulShutdown = this.specificGracefulShutdown;
            if (specificGracefulShutdown == null) {
                return NettyHttpServer.toCompletionStage(channel.close());
            } else {
                return specificGracefulShutdown.shutdownGracefully();
            }
        }

        @Override
        public Optional<ShutdownState> reportShutdownState() {
            return Optional.of(Optional.ofNullable(this.specificGracefulShutdown)
                .flatMap(GracefulShutdownCapable::reportShutdownState)
                .orElse(new SingleShutdownState("Waiting for connection channel to close")));
        }
    }

    final class StreamPipeline {
        HttpVersion httpVersion = HttpVersion.HTTP_1_1;
        private final Channel channel;
        private final ChannelPipeline pipeline;
        @Nullable
        private final SslHandlerHolder sslHandler;

        private final NettyServerCustomizer streamCustomizer;

        private StreamPipeline(Channel channel, @Nullable SslHandlerHolder sslHandler, NettyServerCustomizer streamCustomizer) {
            this.channel = channel;
            this.pipeline = channel.pipeline();
            this.sslHandler = sslHandler;
            this.streamCustomizer = streamCustomizer;
        }

        void initializeChildPipelineForPushPromise(Channel childChannel) {
            StreamPipeline promisePipeline = new StreamPipeline(childChannel, sslHandler, streamCustomizer.specializeForChannel(childChannel, NettyServerCustomizer.ChannelRole.PUSH_PROMISE_STREAM));
            promisePipeline.insertHttp2FrameHandlers();
            promisePipeline.streamCustomizer.onStreamPipelineBuilt();
        }

        private void insertHttp2FrameHandlers() {
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_DECODER, new Http2StreamFrameToHttpObjectCodec(true, server.getServerConfiguration().isValidateHeaders()));

            insertHttp2DownstreamHandlers();
        }

        private void insertHttp3FrameHandlers() {
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_DECODER, new Http3FrameToHttpObjectCodec(true, server.getServerConfiguration().isValidateHeaders()));

            insertHttp2DownstreamHandlers();
        }

        /**
         * Insert the handlers downstream of the {@value ChannelPipelineCustomizer#HANDLER_HTTP2_CONNECTION}. Used both
         * for ALPN HTTP 2 and h2c.
         */
        private void insertHttp2DownstreamHandlers() {
            httpVersion = HttpVersion.HTTP_2_0;

            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_FLOW_CONTROL, new FlowControlHandler());
            if (accessLogHandler != null) {
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER, accessLogHandler);
            }

            registerMicronautChannelHandlers();

            insertMicronautHandlers();
        }

        /**
         * Insert the handlers that manage the micronaut message handling, e.g. conversion between micronaut requests
         * and netty requests, and routing.
         */
        private GracefulShutdownCapable insertMicronautHandlers() {
            channel.attr(STREAM_PIPELINE_ATTRIBUTE.get()).set(this);
            if (sslHandler != null) {
                channel.attr(CERTIFICATE_SUPPLIER_ATTRIBUTE.get()).set(sslHandler.findPeerCert());
            }

            Optional<NettyServerWebSocketUpgradeHandler> webSocketUpgradeHandler = embeddedServices.getWebSocketUpgradeHandler(server);
            if (webSocketUpgradeHandler.isPresent()) {
                pipeline.addLast(NettyServerWebSocketUpgradeHandler.COMPRESSION_HANDLER, new WebSocketServerCompressionHandler());
            }
            if (server.getServerConfiguration().getServerType() != NettyHttpServerConfiguration.HttpServerType.STREAMED) {
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_AGGREGATOR,
                    new HttpObjectAggregator(
                        (int) server.getServerConfiguration().getMaxRequestSize(),
                        server.getServerConfiguration().isCloseOnExpectationFailed()
                    )
                );
            }

            RequestHandler requestHandler = makeRequestHandler(webSocketUpgradeHandler, sslHandler != null);
            PipeliningServerHandler pipeliningServerHandler = new PipeliningServerHandler(requestHandler);
            pipeliningServerHandler.setCompressionStrategy(embeddedServices.getHttpCompressionStrategy());
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND, pipeliningServerHandler);
            return pipeliningServerHandler;
        }

        void afterHttp2ServerHandlerSetUp() {
            httpVersion = HttpVersion.HTTP_2_0;
            channel.attr(STREAM_PIPELINE_ATTRIBUTE.get()).set(this);
            if (sslHandler != null) {
                channel.attr(CERTIFICATE_SUPPLIER_ATTRIBUTE.get()).set(sslHandler.findPeerCert());
            }
        }

        /**
         * Insert the handlers for HTTP 1 that are upstream of the
         * {@value ChannelPipelineCustomizer#HANDLER_HTTP_SERVER_CODEC}. Used both for normal HTTP 1 connections, and
         * after a H2C negotiation failure.
         */
        private GracefulShutdownCapable insertHttp1DownstreamHandlers() {
            httpVersion = HttpVersion.HTTP_1_1;
            if (accessLogHandler != null) {
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER, accessLogHandler);
            }
            registerMicronautChannelHandlers();

            return insertMicronautHandlers();
        }

        /**
         * Add handlers registered through {@link NettyEmbeddedServices#getOutboundHandlers()}.
         */
        private void registerMicronautChannelHandlers() {
            int i = 0;
            for (ChannelOutboundHandler outboundHandlerAdapter : embeddedServices.getOutboundHandlers()) {
                String name;
                if (outboundHandlerAdapter instanceof Named named) {
                    name = named.getName();
                } else {
                    name = ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND + NettyHttpServer.OUTBOUND_KEY + ++i;
                }
                pipeline.addLast(name, outboundHandlerAdapter);
            }
        }
    }

    private static abstract class Http23GracefulShutdownBase implements GracefulShutdownCapable {
        final ChannelHandlerContext ctx;

        Http23GracefulShutdownBase(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public @NonNull Optional<ShutdownState> reportShutdownState() {
            return Optional.of(new SingleShutdownState("Waiting for client to terminate the HTTP/2 connection. Still active streams: " + numberOfActiveStreams()));
        }

        @Override
        public CompletionStage<?> shutdownGracefully() {
            if (ctx.executor().inEventLoop()) {
                shutdownGracefully0();
            } else {
                ctx.executor().execute(this::shutdownGracefully0);
            }

            return NettyHttpServer.toCompletionStage(ctx.channel().closeFuture());
        }

        private void shutdownGracefully0() {
            goAway()
                .addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        ctx.close();
                    }
                });
            ctx.flush();
        }

        protected abstract int numberOfActiveStreams();

        protected abstract ChannelFuture goAway();
    }

    private static final class Http2GracefulShutdown extends Http23GracefulShutdownBase {
        private final Http2ConnectionHandler connectionHandler;

        public Http2GracefulShutdown(ChannelHandlerContext ctx, Http2ConnectionHandler connectionHandler) {
            super(ctx);
            this.connectionHandler = connectionHandler;
        }

        @Override
        protected int numberOfActiveStreams() {
            return connectionHandler.connection().numActiveStreams();
        }

        @Override
        protected ChannelFuture goAway() {
            return connectionHandler.goAway(ctx, connectionHandler.connection().remote().lastStreamCreated(), Http2Error.NO_ERROR.code(), Unpooled.EMPTY_BUFFER, ctx.newPromise());
        }
    }

    private static final class Http3GracefulShutdown extends Http23GracefulShutdownBase {
        private final AtomicLong maxStreamId;

        public Http3GracefulShutdown(ChannelHandlerContext ctx, AtomicLong maxStreamId) {
            super(ctx);
            this.maxStreamId = maxStreamId;
        }

        String key() {
            QuicChannel quicChannel = (QuicChannel) ctx.channel();
            return "c:" + HttpPipelineBuilder.toString(quicChannel.remoteSocketAddress()) + " s:" + HttpPipelineBuilder.toString(quicChannel.localSocketAddress()) + " cid:" + quicChannel.id().asLongText();
        }

        @Override
        protected int numberOfActiveStreams() {
            return -1; // not sure how to count these
        }

        @Override
        protected ChannelFuture goAway() {
            QuicStreamChannel controlStream = Http3.getLocalControlStream(ctx.channel());
            if (controlStream == null) {
                return ctx.close();
            }
            return controlStream.writeAndFlush(new DefaultHttp3GoAwayFrame(maxStreamId.get() + 4));
        }
    }
}
