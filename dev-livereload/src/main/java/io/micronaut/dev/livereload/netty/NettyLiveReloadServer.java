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
import io.micronaut.dev.livereload.LiveReloadServerFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
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
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A LiveReload server on Netty: the {@code hello} handshake and the {@code reload} command with
 * {@code path} and {@code liveCSS} over WebSocket at {@code /livereload}, and {@code /livereload.js}
 * for pages that do not use a browser extension. It also serves mounted directories, with the client
 * script in their HTML, and an event channel at {@code /micronaut-dev/events} for pages that follow
 * a topic. It listens on the loopback address only, on an event loop of its own, so it belongs to the
 * launcher and outlives every application context.
 * The sockets accept native clients, the browser extensions, pages of a localhost origin and those of the origins
 * configured, and refuse other sites and other paths: see {@link AllowedOrigins}.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class NettyLiveReloadServer implements LiveReloadServer {

    private static final Logger LOG = LoggerFactory.getLogger(NettyLiveReloadServer.class);
    private static final String PROTOCOL = "http://livereload.com/protocols/official-7";
    private static final String WEBSOCKET_PATH = "/livereload";
    private static final String SCRIPT_RESOURCE = "META-INF/micronaut-dev/livereload.js";
    private static final AttributeKey<Boolean> GREETED = AttributeKey.valueOf("micronaut-dev-livereload-greeted");
    private static final int MAX_CONTENT_LENGTH = 64 * 1024;
    private static final long MAX_INJECTED_PAGE = 16L * 1024 * 1024;

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
        Map.entry("html", "text/html; charset=utf-8"),
        Map.entry("htm", "text/html; charset=utf-8"),
        Map.entry("css", "text/css; charset=utf-8"),
        Map.entry("js", "application/javascript; charset=utf-8"),
        Map.entry("json", "application/json"),
        Map.entry("ndjson", "application/x-ndjson"),
        Map.entry("xml", "application/xml"),
        Map.entry("txt", "text/plain; charset=utf-8"),
        Map.entry("log", "text/plain; charset=utf-8"),
        Map.entry("svg", "image/svg+xml"),
        Map.entry("png", "image/png"),
        Map.entry("ico", "image/x-icon")
    );

    private final EventLoopGroup group;
    private final Channel channel;
    private final ChannelGroup browsers;
    private final Server state;
    private final AtomicBoolean closed = new AtomicBoolean();

    private NettyLiveReloadServer(EventLoopGroup group, Channel channel, ChannelGroup browsers, Server state) {
        this.group = group;
        this.channel = channel;
        this.browsers = browsers;
        this.state = state;
    }

    /**
     * Starts a server on the loopback address.
     *
     * @param port The port, {@link #DEFAULT_PORT} for the extensions; 0 for any free port
     * @return The started server
     * @throws IOException if the port cannot be bound
     */
    public static NettyLiveReloadServer start(int port) throws IOException {
        return start(port, List.of());
    }

    /**
     * Starts a server on the loopback address whose socket also accepts the pages of the origins given, beside the
     * clients it always accepts: see {@link LiveReloadServerFactory#start(int, List)}.
     *
     * @param port The port, {@link #DEFAULT_PORT} for the extensions; 0 for any free port
     * @param allowedOrigins The origins, such as {@code http://devbox.lan:8080}, or host names, whose pages may connect
     * @return The started server
     * @throws IOException if the port cannot be bound
     */
    public static NettyLiveReloadServer start(int port, List<String> allowedOrigins) throws IOException {
        AllowedOrigins origins = AllowedOrigins.of(allowedOrigins);
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, runnable -> {
            Thread thread = new Thread(runnable, "micronaut-dev-livereload");
            thread.setDaemon(true);
            return thread;
        }, NioIoHandler.newFactory());
        ChannelGroup browsers = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        Server state = new Server(browsers, origins);
        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(group)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    // the WebSocket handlers are added on the upgrade, by its path: the LiveReload protocol or the event channel
                    socketChannel.pipeline()
                        .addLast(new HttpServerCodec())
                        .addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH))
                        .addLast(new RequestHandler(state));
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
        NettyLiveReloadServer server = new NettyLiveReloadServer(group, channel, browsers, state);
        state.port = server.port();
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
    public String serve(String prefix, Path directory) {
        String normalized = normalizePrefix(prefix);
        state.mounts.put(normalized, directory.toAbsolutePath().normalize());
        return "http://localhost:" + port() + normalized;
    }

    @Override
    public void unserve(String prefix) {
        state.mounts.remove(normalizePrefix(prefix));
    }

    @Override
    public void publish(String topic, String json) {
        ChannelGroup subscribers = state.topics.get(topic);
        if (subscribers != null) {
            subscribers.writeAndFlush(new TextWebSocketFrame(json));
        }
    }

    @Override
    public int subscribers(String topic) {
        ChannelGroup subscribers = state.topics.get(topic);
        return subscribers == null ? 0 : subscribers.size();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        browsers.close();
        state.topics.values().forEach(ChannelGroup::close);
        channel.close();
        group.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS);
    }

    private static String normalizePrefix(String prefix) {
        String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        if (normalized.equals("/") || normalized.startsWith(WEBSOCKET_PATH + "/") || normalized.startsWith(EVENTS_PATH) || normalized.startsWith(SCRIPT_PATH)) {
            throw new IllegalArgumentException("Cannot serve files at " + prefix + ": the path is the server's own");
        }
        return normalized;
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
     * An HTML page with the client script before its closing body tag, or at its end without one.
     */
    static String withScript(String html, int port) {
        String tag = LiveReloadServer.scriptTag(port);
        int body = html.toLowerCase(Locale.ROOT).lastIndexOf("</body>");
        return body < 0 ? html + tag : html.substring(0, body) + tag + html.substring(body);
    }

    /**
     * A mounted file to stream.
     *
     * @param path The file
     * @param contentType Its content type
     */
    private record StreamedFile(Path path, String contentType) {
    }

    /**
     * What the handlers share: the LiveReload clients, the mounted directories, the event channel's topics.
     */
    private static final class Server {
        private final ChannelGroup browsers;
        private final Map<String, Path> mounts = new ConcurrentHashMap<>();
        private final Map<String, ChannelGroup> topics = new ConcurrentHashMap<>();
        private final AllowedOrigins allowedOrigins;
        private volatile int port;

        Server(ChannelGroup browsers, AllowedOrigins allowedOrigins) {
            this.browsers = browsers;
            this.allowedOrigins = allowedOrigins;
        }

        ChannelGroup topic(String topic) {
            return topics.computeIfAbsent(topic, name -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
        }
    }

    /**
     * Serves the client script and the mounted directories, and routes a WebSocket upgrade by its path to the LiveReload
     * protocol, from an allowed origin, or the event channel, from a page this server served; every other request is a
     * 404, an upgrade for another path included.
     */
    private static final class RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private final Server state;

        RequestHandler(Server state) {
            this.state = state;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) throws IOException {
            QueryStringDecoder uri = new QueryStringDecoder(request.uri());
            String path = uri.path();
            // only a WebSocket upgrade goes on to the handshake; a client offering h2c on a plain request, as the
            // JDK client does, gets an answer like any other
            if (request.headers().containsValue(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)) {
                if (path.equals(EVENTS_PATH) && !(isLocalHost(request) && isOwnOrigin(request))) {
                    // a WebSocket is not bound by the same-origin policy: only a page this server served may follow its events
                    forbidden(context);
                    return;
                }
                if (path.equals(EVENTS_PATH)) {
                    String topic = uri.parameters().getOrDefault("topic", List.of("")).getFirst();
                    // the topic is read: the handshake is given the path alone, since Netty's does not complete with a query
                    request.setUri(path);
                    context.pipeline().addLast(new WebSocketServerProtocolHandler(EVENTS_PATH, null, true), new EventsHandler(state, topic));
                } else if (!path.equals(WEBSOCKET_PATH)) {
                    // the handshake would pass an upgrade for another path on, and the connection would stay open
                    refuse(context, HttpResponseStatus.NOT_FOUND, "LiveReload");
                    return;
                } else if (!state.allowedOrigins.allows(request.headers().get(HttpHeaderNames.ORIGIN))) {
                    forbidden(context);
                    return;
                } else {
                    // the handshake is given the path alone, since Netty's does not complete with a query
                    request.setUri(WEBSOCKET_PATH);
                    context.pipeline().addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH, null, true), new ProtocolHandler(state.browsers));
                }
                context.fireChannelRead(request.retain());
                return;
            }
            FullHttpResponse response;
            if (request.method() != HttpMethod.GET && request.method() != HttpMethod.HEAD) {
                response = text(HttpResponseStatus.METHOD_NOT_ALLOWED, "LiveReload");
            } else if (path.startsWith(SCRIPT_PATH)) {
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(script()));
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/javascript; charset=utf-8");
                // the script is public and loaded by the application's pages, whatever their origin
                response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            } else if (!isLocalHost(request)) {
                // a mounted page is read through a localhost name only: another name resolving to the loopback address is a
                // rebinding page reading what it may not
                response = text(HttpResponseStatus.FORBIDDEN, "Forbidden");
            } else {
                Object mounted = mounted(path);
                if (mounted instanceof StreamedFile file) {
                    stream(context, file, request.method() == HttpMethod.HEAD);
                    return;
                }
                response = (FullHttpResponse) mounted;
            }
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
            HttpUtil.setContentLength(response, response.content().readableBytes());
            HttpUtil.setKeepAlive(response, false);
            if (request.method() == HttpMethod.HEAD) {
                response.content().clear();
            }
            context.writeAndFlush(response).addListener(future -> context.close());
        }

        /**
         * A file of the mounted directory with the longest prefix the path is under: {@code index.html} for the directory
         * itself, with the client script in HTML. A file is served only when its real path, links followed, is in the
         * real directory.
         */
        private Object mounted(String path) throws IOException {
            String prefix = null;
            for (String candidate : state.mounts.keySet()) {
                boolean matches = path.startsWith(candidate) || path.equals(candidate.substring(0, candidate.length() - 1));
                if (matches && (prefix == null || candidate.length() > prefix.length())) {
                    prefix = candidate;
                }
            }
            Path root = prefix == null ? null : state.mounts.get(prefix);
            if (prefix == null || root == null) {
                return text(HttpResponseStatus.NOT_FOUND, "LiveReload");
            }
            if (path.length() < prefix.length()) {
                // relative links resolve against the directory only with the slash
                FullHttpResponse redirect = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.MOVED_PERMANENTLY, Unpooled.EMPTY_BUFFER);
                redirect.headers().set(HttpHeaderNames.LOCATION, prefix);
                return redirect;
            }
            String relative = path.substring(prefix.length());
            Path file;
            try {
                Path realRoot = root.toRealPath();
                file = realRoot.resolve(relative).normalize().toRealPath();
                if (!file.startsWith(realRoot)) {
                    return text(HttpResponseStatus.NOT_FOUND, "Not found");
                }
                if (Files.isDirectory(file)) {
                    if (!path.endsWith("/")) {
                        // a directory deeper in the mount too: relative links resolve against it only with the slash
                        FullHttpResponse redirect = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.MOVED_PERMANENTLY, Unpooled.EMPTY_BUFFER);
                        redirect.headers().set(HttpHeaderNames.LOCATION, path + "/");
                        return redirect;
                    }
                    file = file.resolve("index.html").toRealPath();
                }
                if (!file.startsWith(realRoot) || !Files.isRegularFile(file)) {
                    return text(HttpResponseStatus.NOT_FOUND, "Not found");
                }
            } catch (IOException | java.nio.file.InvalidPathException e) {
                return text(HttpResponseStatus.NOT_FOUND, "Not found");
            }
            String name = file.getFileName().toString();
            String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            String contentType = CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
            boolean html = extension.equals("html") || extension.equals("htm");
            if (!html || Files.size(file) > MAX_INJECTED_PAGE) {
                // the file goes to the socket as it is, from the file system, without passing through the heap
                return new StreamedFile(file, contentType);
            }
            // a page carries the client script, so it is read: pages are small, and a large one goes as it is above
            byte[] content = withScript(Files.readString(file, StandardCharsets.UTF_8), state.port).getBytes(StandardCharsets.UTF_8);
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(content));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
            return response;
        }

        /**
         * Writes a file to the connection as a file region, the headers first; a {@code HEAD} request gets the headers only.
         */
        private static void stream(ChannelHandlerContext context, StreamedFile file, boolean head) throws IOException {
            long length = Files.size(file.path());
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, file.contentType());
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
            HttpUtil.setContentLength(response, length);
            HttpUtil.setKeepAlive(response, false);
            context.write(response);
            if (!head) {
                context.write(new DefaultFileRegion(file.path().toFile(), 0, length));
            }
            context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(future -> context.close());
        }

        /**
         * Whether the request names this machine by a localhost name, or the loopback address, in its host header.
         */
        private static boolean isLocalHost(FullHttpRequest request) {
            String host = request.headers().get(HttpHeaderNames.HOST);
            if (host == null) {
                return false;
            }
            String name = host.startsWith("[") ? host.substring(0, host.indexOf(']') + 1) : host.contains(":") ? host.substring(0, host.lastIndexOf(':')) : host;
            name = name.toLowerCase(Locale.ROOT);
            return name.equals("localhost") || name.endsWith(".localhost") || name.equals("127.0.0.1") || name.equals("[::1]");
        }

        /**
         * Whether a request comes from a page this server served, or from a client that is no page, which sends no origin.
         */
        private boolean isOwnOrigin(FullHttpRequest request) {
            String origin = request.headers().get(HttpHeaderNames.ORIGIN);
            if (origin == null) {
                return true;
            }
            try {
                java.net.URI uri = new java.net.URI(origin);
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                // a browser leaves the default port out of an origin
                int port = uri.getPort() < 0 ? 80 : uri.getPort();
                return "http".equalsIgnoreCase(uri.getScheme()) && (host.equals("localhost") || host.equals("127.0.0.1")) && port == state.port;
            } catch (java.net.URISyntaxException e) {
                return false;
            }
        }

        private static void forbidden(ChannelHandlerContext context) {
            refuse(context, HttpResponseStatus.FORBIDDEN, "Forbidden");
        }

        private static void refuse(ChannelHandlerContext context, HttpResponseStatus status, String body) {
            FullHttpResponse response = text(status, body);
            HttpUtil.setContentLength(response, response.content().readableBytes());
            HttpUtil.setKeepAlive(response, false);
            context.writeAndFlush(response).addListener(future -> context.close());
        }

        private static FullHttpResponse text(HttpResponseStatus status, String body) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
            return response;
        }
    }

    /**
     * A page listening to a topic of the event channel; what it sends is ignored.
     */
    private static final class EventsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

        private final Server state;
        private final String topic;

        EventsHandler(Server state, String topic) {
            this.state = state;
            this.topic = topic;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
            if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                state.topic(topic).add(context.channel());
            }
            super.userEventTriggered(context, event);
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) throws Exception {
            // the server outlives every page: a topic goes with its last listener
            state.topics.computeIfPresent(topic, (name, subscribers) -> {
                subscribers.remove(context.channel());
                return subscribers.isEmpty() ? null : subscribers;
            });
            super.channelInactive(context);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, TextWebSocketFrame frame) {
            // the channel only sends
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            LOG.debug("Event channel connection failed: {}", cause.getMessage());
            context.close();
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
