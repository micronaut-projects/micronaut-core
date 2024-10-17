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
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.handler.Http2ServerHandler;
import io.micronaut.http.server.netty.handler.PipeliningServerHandler;
import io.micronaut.http.server.netty.handler.RequestHandler;
import io.micronaut.http.server.netty.handler.accesslog.Http2AccessLogManager;
import io.micronaut.http.server.netty.handler.accesslog.HttpAccessLogHandler;
import io.micronaut.http.server.netty.websocket.NettyServerWebSocketUpgradeHandler;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
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
import javax.net.ssl.SSLSession;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
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
    static final Supplier<AttributeKey<Supplier<SSLSession>>> SSL_SESSION_ATTRIBUTE =
        SupplierUtil.memoized(() -> AttributeKey.newInstance("ssl-session"));

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
    private final Http2AccessLogManager.Factory accessLogManagerFactory;

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
        if (accessLogger != null && accessLogger.isEnabled()) {
            String loggerName = accessLogger.getLoggerName();
            Predicate<String> uriInclusion = NettyHttpServer.inclusionPredicate(accessLogger);
            accessLogHandler = new HttpAccessLogHandler(loggerName, accessLogger.getLogFormat(), uriInclusion);
            accessLogManagerFactory = new Http2AccessLogManager.Factory(loggerName == null || loggerName.isEmpty() ? null : LoggerFactory.getLogger(loggerName), accessLogger.getLogFormat(), uriInclusion);
            routingInBoundHandler.supportLoggingHandler = true;
        } else {
            accessLogHandler = null;
            accessLogManagerFactory = null;
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

    private BodySizeLimits bodySizeLimits() {
        return new BodySizeLimits(server.getServerConfiguration().getMaxRequestSize(), server.getServerConfiguration().getMaxRequestBufferSize());
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

        Supplier<SSLSession> findSslSession() {
            return SupplierUtil.memoized(() -> (quicSslEngine == null ? sslHandler.engine() : quicSslEngine).getSession());
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

    final class ConnectionPipeline {
        private final Channel channel;
        private final ChannelPipeline pipeline;

        @Nullable
        private final SslHandlerHolder sslHandler;

        private final NettyServerCustomizer connectionCustomizer;

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
                        insertPcapLoggingHandler(ch, "quic-decapsulated");
                        ch.pipeline().addLast(new Http3ServerConnectionHandler(new ChannelInitializer<QuicStreamChannel>() {
                            @Override
                            protected void initChannel(@NonNull QuicStreamChannel ch) throws Exception {
                                StreamPipeline streamPipeline = new StreamPipeline(ch, sslHandler, connectionCustomizer.specializeForChannel(ch, NettyServerCustomizer.ChannelRole.REQUEST_STREAM));
                                streamPipeline.insertHttp3FrameHandlers();
                                streamPipeline.streamCustomizer.onStreamPipelineBuilt();
                            }
                        }));
                    }
                })
                .build());
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

            new StreamPipeline(channel, sslHandler, connectionCustomizer).insertHttp1DownstreamHandlers();

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
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, createHttp2FrameCodec());
                pipeline.addLast(makeHttp2Handler());
            } else {
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, createHttp2ServerHandler(true));
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
                .bodySizeLimits(bodySizeLimits())
                .accessLogManagerFactory(accessLogManagerFactory)
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

            final Http2FrameCodec frameCodec;
            final Http2ConnectionHandler connectionHandler;
            Http2MultiplexHandler multiplexHandler;
            if (server.getServerConfiguration().isLegacyMultiplexHandlers()) {
                frameCodec = createHttp2FrameCodec();
                connectionHandler = frameCodec;
                multiplexHandler = new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                    @Override
                    protected void initChannel(@NonNull Http2StreamChannel ch) {
                        StreamPipeline streamPipeline = new StreamPipeline(ch, sslHandler, connectionCustomizer.specializeForChannel(ch, NettyServerCustomizer.ChannelRole.REQUEST_STREAM));
                        streamPipeline.insertHttp2FrameHandlers();
                        streamPipeline.streamCustomizer.onStreamPipelineBuilt();
                    }
                });
            } else {
                connectionHandler = createHttp2ServerHandler(false);
                frameCodec = null;
                multiplexHandler = null;
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
                            onRequestPipelineBuilt();
                        }
                    }

                    if (frameCodec == null) {
                        return new Http2ServerUpgradeCodecImpl(connectionHandler);
                    } else {
                        return new Http2ServerUpgradeCodecImpl(frameCodec, multiplexHandler);
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
            ChannelHandler priorKnowledgeHandler = frameCodec == null ? connectionHandler : new ChannelInitializer<>() {
                @Override
                protected void initChannel(@NonNull Channel ch) {
                    ch.pipeline().addLast(connectionHandler, multiplexHandler);
                }
            };
            final CleartextHttp2ServerUpgradeHandler cleartextHttp2ServerUpgradeHandler =
                    new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, priorKnowledgeHandler);

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
                    new StreamPipeline(channel, sslHandler, connectionCustomizer).insertHttp1DownstreamHandlers();
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
        private void insertMicronautHandlers() {
            channel.attr(STREAM_PIPELINE_ATTRIBUTE.get()).set(this);
            if (sslHandler != null) {
                channel.attr(SSL_SESSION_ATTRIBUTE.get()).set(sslHandler.findSslSession());
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
            pipeliningServerHandler.setBodySizeLimits(bodySizeLimits());
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND, pipeliningServerHandler);
        }

        void afterHttp2ServerHandlerSetUp() {
            httpVersion = HttpVersion.HTTP_2_0;
            channel.attr(STREAM_PIPELINE_ATTRIBUTE.get()).set(this);
            if (sslHandler != null) {
                channel.attr(SSL_SESSION_ATTRIBUTE.get()).set(sslHandler.findSslSession());
            }
        }

        /**
         * Insert the handlers for HTTP 1 that are upstream of the
         * {@value ChannelPipelineCustomizer#HANDLER_HTTP_SERVER_CODEC}. Used both for normal HTTP 1 connections, and
         * after a H2C negotiation failure.
         */
        private void insertHttp1DownstreamHandlers() {
            httpVersion = HttpVersion.HTTP_1_1;
            if (accessLogHandler != null) {
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER, accessLogHandler);
            }
            registerMicronautChannelHandlers();

            insertMicronautHandlers();
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
}
