/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev.livereload.netty;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A LiveReload server on Netty: the {@code hello} handshake and the {@code reload} command with
 * {@code path} and {@code liveCSS} over WebSocket at {@code /livereload}, and {@code /livereload.js}
 * for pages that do not use a browser extension. It listens on the loopback address only, on an
 * event loop of its own, so it belongs to the launcher and outlives every application context.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class NettyLiveReloadServer implements LiveReloadServer {

    private static final Logger LOG = LoggerFactory.getLogger(NettyLiveReloadServer.class);
    private static final String PROTOCOL = "http://livereload.com/protocols/official-7";
    private static final String WEBSOCKET_PATH = "/livereload";
    private static final String SCRIPT_RESOURCE = "META-INF/micronaut-dev/livereload.js";
    private static final AttributeKey<Boolean> GREETED = AttributeKey.valueOf("micronaut-dev-livereload-greeted");
    private static final int MAX_CONTENT_LENGTH = 64 * 1024;

    private final EventLoopGroup group;
    private final Channel channel;
    private final ChannelGroup browsers;
    private final AtomicBoolean closed = new AtomicBoolean();

    private NettyLiveReloadServer(EventLoopGroup group, Channel channel, ChannelGroup browsers) {
        this.group = group;
        this.channel = channel;
        this.browsers = browsers;
    }

    /**
     * Starts a server on the loopback address.
     *
     * @param port The port, {@link #DEFAULT_PORT} for the extensions; 0 for any free port
     * @return The started server
     * @throws IOException if the port cannot be bound
     */
    public static NettyLiveReloadServer start(int port) throws IOException {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, runnable -> {
            Thread thread = new Thread(runnable, "micronaut-dev-livereload");
            thread.setDaemon(true);
            return thread;
        }, NioIoHandler.newFactory());
        ChannelGroup browsers = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(group)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline()
                        .addLast(new HttpServerCodec())
                        .addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH))
                        .addLast(new ScriptHandler())
                        .addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH, null, true))
                        .addLast(new ProtocolHandler(browsers));
                }
            });
        Channel channel;
        try {
            // the IPv4 loopback explicitly: "localhost" in the script tag reaches it whatever the JVM prefers, and a
            // client that only speaks IPv4 is not left out when the JVM prefers IPv6 addresses
            channel = bootstrap.bind(new InetSocketAddress(InetAddress.getByAddress("localhost", new byte[] {127, 0, 0, 1}), port)).sync().channel();
        } catch (Exception e) {
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            throw new IOException("Cannot bind the LiveReload port " + port + ": " + e.getMessage(), e);
        }
        NettyLiveReloadServer server = new NettyLiveReloadServer(group, channel, browsers);
        LOG.info("LiveReload server listening on port {}", server.port());
        return server;
    }

    @Override
    public int port() {
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    @Override
    public int connections() {
        return browsers.size();
    }

    @Override
    public void reload(String path, boolean liveCss) {
        String command = "{\"command\":\"reload\",\"path\":\"" + escape(path) + "\",\"liveCSS\":" + liveCss + "}";
        browsers.writeAndFlush(new TextWebSocketFrame(command));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        browsers.close();
        channel.close();
        group.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS);
    }

    static byte[] script() throws IOException {
        try (InputStream in = NettyLiveReloadServer.class.getClassLoader().getResourceAsStream(SCRIPT_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing " + SCRIPT_RESOURCE);
            }
            return in.readAllBytes();
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Serves the client script; every other plain request is a 404, and an upgrade passes through.
     */
    private static final class ScriptHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) throws IOException {
            // only a WebSocket upgrade goes on to the handshake; a client offering h2c on a plain request, as the
            // JDK client does, gets the script like any other
            if (request.headers().containsValue(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)) {
                context.fireChannelRead(request.retain());
                return;
            }
            FullHttpResponse response;
            if (request.method() == HttpMethod.GET && request.uri().startsWith(SCRIPT_PATH)) {
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(script()));
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/javascript; charset=utf-8");
            } else {
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, Unpooled.copiedBuffer("LiveReload", StandardCharsets.UTF_8));
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
            }
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            HttpUtil.setContentLength(response, response.content().readableBytes());
            HttpUtil.setKeepAlive(response, false);
            context.writeAndFlush(response).addListener(future -> context.close());
        }
    }

    /**
     * The LiveReload protocol over the WebSocket frames Netty decoded.
     */
    private static final class ProtocolHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

        private final ChannelGroup browsers;

        ProtocolHandler(ChannelGroup browsers) {
            this.browsers = browsers;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
            if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                browsers.add(context.channel());
            }
            super.userEventTriggered(context, event);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, TextWebSocketFrame frame) {
            if (frame.text().contains("\"hello\"")) {
                context.channel().attr(GREETED).set(Boolean.TRUE);
                context.writeAndFlush(new TextWebSocketFrame("{\"command\":\"hello\",\"protocols\":[\"" + PROTOCOL + "\"],\"serverName\":\"micronaut-dev\"}"));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            LOG.debug("LiveReload connection failed: {}", cause.getMessage());
            context.close();
        }
    }
}
