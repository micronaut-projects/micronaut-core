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
import io.micronaut.core.naming.Named;
import io.micronaut.http.context.event.HttpRequestReceivedEvent;
import io.micronaut.http.netty.AbstractNettyHttpRequest;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.netty.stream.HttpStreamsServerHandler;
import io.micronaut.http.netty.stream.StreamingInboundHttp2ToHttpAdapter;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.decoders.HttpRequestDecoder;
import io.micronaut.http.server.netty.encoders.HttpResponseEncoder;
import io.micronaut.http.server.netty.handler.accesslog.HttpAccessLogHandler;
import io.micronaut.http.server.netty.ssl.HttpRequestCertificateHandler;
import io.micronaut.http.server.netty.websocket.NettyServerWebSocketUpgradeHandler;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.pcap.PcapWriteHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Helper class that manages the {@link ChannelPipeline} of incoming HTTP connections.
 *
 * This outer class is basically a singleton, though it may be replaced when the configuration is reloaded. Inner
 * classes are connection-scoped.
 *
 * @since 3.4
 * @author ywkat
 */
final class HttpPipelineBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(HttpPipelineBuilder.class);

    private final NettyHttpServer server;
    private final NettyEmbeddedServices embeddedServices;
    private final ServerSslConfiguration sslConfiguration;
    private final RoutingInBoundHandler routingInBoundHandler;
    private final HttpHostResolver hostResolver;

    private final LoggingHandler loggingHandler;
    private final SslContext sslContext;
    private final HttpAccessLogHandler accessLogHandler;
    private final HttpRequestDecoder requestDecoder;
    private final HttpResponseEncoder responseEncoder;

    private final HttpRequestCertificateHandler requestCertificateHandler = new HttpRequestCertificateHandler();

    private final NettyServerCustomizer serverCustomizer;

    HttpPipelineBuilder(NettyHttpServer server, NettyEmbeddedServices embeddedServices, ServerSslConfiguration sslConfiguration, RoutingInBoundHandler routingInBoundHandler, HttpHostResolver hostResolver, NettyServerCustomizer serverCustomizer) {
        this.server = server;
        this.embeddedServices = embeddedServices;
        this.sslConfiguration = sslConfiguration;
        this.routingInBoundHandler = routingInBoundHandler;
        this.hostResolver = hostResolver;
        this.serverCustomizer = serverCustomizer;

        loggingHandler = server.getServerConfiguration().getLogLevel().isPresent() ? new LoggingHandler(NettyHttpServer.class, server.getServerConfiguration().getLogLevel().get()) : null;
        sslContext = embeddedServices.getServerSslBuilder() != null ? embeddedServices.getServerSslBuilder().build().orElse(null) : null;

        NettyHttpServerConfiguration.AccessLogger accessLogger = server.getServerConfiguration().getAccessLogger();
        if (accessLogger != null && accessLogger.isEnabled()) {
            accessLogHandler = new HttpAccessLogHandler(accessLogger.getLoggerName(), accessLogger.getLogFormat(), NettyHttpServer.inclusionPredicate(accessLogger));
        } else {
            accessLogHandler = null;
        }

        requestDecoder = new HttpRequestDecoder(server,
                server.getEnvironment(),
                server.getServerConfiguration(),
                embeddedServices.getEventPublisher(HttpRequestReceivedEvent.class));
        responseEncoder = new HttpResponseEncoder(
                embeddedServices.getMediaTypeCodecRegistry(),
                server.getServerConfiguration()
        );
    }

    boolean supportsSsl() {
        return sslContext != null;
    }

    final class ConnectionPipeline {
        private final Channel channel;
        private final ChannelPipeline pipeline;

        private final boolean ssl;

        private final NettyServerCustomizer connectionCustomizer;

        ConnectionPipeline(Channel channel, boolean ssl) {
            this.channel = channel;
            this.pipeline = channel.pipeline();
            this.ssl = ssl;
            this.connectionCustomizer = serverCustomizer.specializeForChannel(channel, NettyServerCustomizer.ChannelRole.CONNECTION);
        }

        void insertPcapLoggingHandler(String qualifier) {
            String pattern = server.getServerConfiguration().getPcapLoggingPathPattern();
            if (pattern == null) {
                return;
            }

            String path = pattern;
            path = path.replace("{qualifier}", qualifier);
            path = path.replace("{localAddress}", resolveIfNecessary(pipeline.channel().localAddress()));
            path = path.replace("{remoteAddress}", resolveIfNecessary(pipeline.channel().remoteAddress()));
            path = path.replace("{random}", Long.toHexString(ThreadLocalRandom.current().nextLong()));
            path = path.replace("{timestamp}", Instant.now().toString());

            path = path.replace(':', '_'); // for windows

            LOG.warn("Logging *full* request data, as configured. This will contain sensitive information! Path: '{}'", path);

            try {
                pipeline.addLast(new PcapWriteHandler(new FileOutputStream(path)));
            } catch (FileNotFoundException e) {
                LOG.warn("Failed to create target pcap at '{}', not logging.", path, e);
            }
        }

        /**
         * Force resolution of the given address, and then transform it to string. This prevents any potential user data
         * appearing in the file path
         */
        private String resolveIfNecessary(SocketAddress address) {
            if (address instanceof InetSocketAddress) {
                if (((InetSocketAddress) address).isUnresolved()) {
                    // try resolution
                    address = new InetSocketAddress(((InetSocketAddress) address).getHostString(), ((InetSocketAddress) address).getPort());
                    if (((InetSocketAddress) address).isUnresolved()) {
                        // resolution failed, bail
                        return "unresolved";
                    }
                }
                return ((InetSocketAddress) address).getAddress().getHostAddress() + ':' + ((InetSocketAddress) address).getPort();
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
                if (ssl) {
                    configureForAlpn();
                } else {
                    configureForH2cSupport();
                }
            }
        }

        /**
         * Insert handlers that wrap the outermost TCP stream. This is SSL and potentially packet capture.
         */
        void insertOuterTcpHandlers() {
            insertPcapLoggingHandler("encapsulated");

            if (ssl) {
                SslHandler sslHandler = sslContext.newHandler(channel.alloc());
                sslHandler.setHandshakeTimeoutMillis(sslConfiguration.getHandshakeTimeout().toMillis());
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_SSL, sslHandler);

                insertPcapLoggingHandler("ssl-decapsulated");
            }

            if (loggingHandler != null) {
                pipeline.addLast(loggingHandler);
            }
        }

        private void onRequestPipelineBuilt() {
            server.triggerPipelineListeners(pipeline);
            connectionCustomizer.onStreamPipelineBuilt();
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
         * Insert the handlers that manage the micronaut message handling, e.g. conversion between micronaut requests
         * and netty requests, and routing.
         */
        private void insertMicronautHandlers() {
            pipeline.addLast(NettyServerWebSocketUpgradeHandler.COMPRESSION_HANDLER, new WebSocketServerCompressionHandler());
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_STREAM, new HttpStreamsServerHandler());
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CHUNK, new ChunkedWriteHandler());
            pipeline.addLast(HttpRequestDecoder.ID, requestDecoder);
            if (server.getServerConfiguration().isDualProtocol() && server.getServerConfiguration().isHttpToHttpsRedirect() && !ssl) {
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_TO_HTTPS_REDIRECT, new HttpToHttpsRedirectHandler(sslConfiguration, hostResolver));
            }
            if (ssl) {
                pipeline.addLast("request-certificate-handler", requestCertificateHandler);
            }
            pipeline.addLast(HttpResponseEncoder.ID, responseEncoder);
            pipeline.addLast(NettyServerWebSocketUpgradeHandler.ID, new NettyServerWebSocketUpgradeHandler(
                    embeddedServices,
                    server.getWebSocketSessionRepository()));
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND, routingInBoundHandler);
        }

        /**
         * Configure this pipeline for normal HTTP 1.
         */
        void configureForHttp1() {
            insertIdleStateHandler();

            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_SERVER_CODEC, createServerCodec());

            insertHttp1DownstreamHandlers();

            connectionCustomizer.onInitialPipelineBuilt();
            onRequestPipelineBuilt();
        }

        /**
         * Insert the handlers for HTTP 1 that are upstream of the
         * {@value ChannelPipelineCustomizer#HANDLER_HTTP_SERVER_CODEC}. Used both for normal HTTP 1 connections, and
         * after a H2C negotiation failure.
         */
        private void insertHttp1DownstreamHandlers() {
            if (accessLogHandler != null) {
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER, accessLogHandler);
            }
            registerMicronautChannelHandlers();
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_FLOW_CONTROL, new FlowControlHandler());
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_KEEP_ALIVE, new HttpServerKeepAliveHandler());
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_COMPRESSOR, new SmartHttpContentCompressor(embeddedServices.getHttpCompressionStrategy()));
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP_DECOMPRESSOR, new HttpContentDecompressor());

            insertMicronautHandlers();
        }

        /**
         * Insert the handlers for normal HTTP 2, after ALPN.
         */
        private void configureForHttp2() {
            insertIdleStateHandler();

            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, newHttpToHttp2ConnectionHandler());
            registerMicronautChannelHandlers();

            insertHttp2DownstreamHandlers();

            connectionCustomizer.onInitialPipelineBuilt();
            onRequestPipelineBuilt();
        }

        /**
         * Insert the handlers downstream of the {@value ChannelPipelineCustomizer#HANDLER_HTTP2_CONNECTION}. Used both
         * for ALPN HTTP 2 and h2c.
         */
        private void insertHttp2DownstreamHandlers() {
            pipeline.addLast(ChannelPipelineCustomizer.HANDLER_FLOW_CONTROL, new FlowControlHandler());
            if (accessLogHandler != null) {
                pipeline.addLast(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER, accessLogHandler);
            }

            insertMicronautHandlers();
        }

        /**
         * Create the HTTP 2 <-> HTTP 1 converter, inserted as
         * {@value ChannelPipelineCustomizer#HANDLER_HTTP2_CONNECTION}.
         */
        private HttpToHttp2ConnectionHandler newHttpToHttp2ConnectionHandler() {
            Http2Connection connection = new DefaultHttp2Connection(true);
            final Http2FrameListener http2ToHttpAdapter = new StreamingInboundHttp2ToHttpAdapter(
                    connection,
                    (int) server.getServerConfiguration().getMaxRequestSize(),
                    server.getServerConfiguration().isValidateHeaders(),
                    true
            );
            final HttpToHttp2ConnectionHandlerBuilder builder = new HttpToHttp2ConnectionHandlerBuilder()
                    .frameListener(http2ToHttpAdapter)
                    .validateHeaders(server.getServerConfiguration().isValidateHeaders())
                    .initialSettings(server.getServerConfiguration().getHttp2().http2Settings());

            server.getServerConfiguration().getLogLevel().ifPresent(logLevel ->
                    builder.frameLogger(new Http2FrameLogger(logLevel,
                            NettyHttpServer.class))
            );
            return builder.connection(connection).build();
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
                    if (evt instanceof SslHandshakeCompletionEvent) {
                        SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
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
                protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
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

            final HttpToHttp2ConnectionHandler connectionHandler = newHttpToHttp2ConnectionHandler();
            final String fallbackHandlerName = "http1-fallback-handler";
            HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory = protocol -> {
                if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {

                    return new Http2ServerUpgradeCodec(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, connectionHandler) {
                        @Override
                        public void upgradeTo(ChannelHandlerContext ctx, FullHttpRequest upgradeRequest) {
                            pipeline.remove(fallbackHandlerName);
                            insertHttp2DownstreamHandlers();
                            onRequestPipelineBuilt();
                            super.upgradeTo(ctx, upgradeRequest);
                            // HTTP1 request is on the implicit stream 1
                            upgradeRequest.headers().set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 1);
                            ctx.fireChannelRead(ReferenceCountUtil.retain(upgradeRequest));
                        }
                    };
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
                    new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(@NonNull Channel ch) throws Exception {
                            ch.pipeline().addLast(connectionHandler);
                            insertHttp2DownstreamHandlers();
                            onRequestPipelineBuilt();
                        }
                    });

            pipeline.addLast(cleartextHttp2ServerUpgradeHandler);
            pipeline.addLast(fallbackHandlerName, new SimpleChannelInboundHandler<HttpMessage>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, HttpMessage msg) {
                    // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
                    if (msg instanceof HttpRequest) {
                        HttpRequest req = (HttpRequest) msg;
                        if (req.headers().contains(AbstractNettyHttpRequest.STREAM_ID)) {
                            ChannelPipeline pipeline = ctx.pipeline();
                            pipeline.remove(this);
                            pipeline.fireChannelRead(ReferenceCountUtil.retain(msg));
                            return;
                        }
                    }
                    ChannelPipeline pipeline = ctx.pipeline();

                    // remove the handlers we don't need anymore
                    pipeline.remove(upgradeHandler);
                    pipeline.remove(this);

                    // reconfigure for http1
                    // note: we have to reuse the serverCodec in case it still has some data buffered
                    insertHttp1DownstreamHandlers();

                    onRequestPipelineBuilt();
                    pipeline.fireChannelRead(ReferenceCountUtil.retain(msg));
                }
            });
            connectionCustomizer.onInitialPipelineBuilt();
        }

        /**
         * Add handlers registered through {@link NettyEmbeddedServices#getOutboundHandlers()}.
         */
        private void registerMicronautChannelHandlers() {
            int i = 0;
            for (ChannelOutboundHandler outboundHandlerAdapter : embeddedServices.getOutboundHandlers()) {
                String name;
                if (outboundHandlerAdapter instanceof Named) {
                    name = ((Named) outboundHandlerAdapter).getName();
                } else {
                    name = ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND + NettyHttpServer.OUTBOUND_KEY + ++i;
                }
                pipeline.addLast(name, outboundHandlerAdapter);
            }
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
}
