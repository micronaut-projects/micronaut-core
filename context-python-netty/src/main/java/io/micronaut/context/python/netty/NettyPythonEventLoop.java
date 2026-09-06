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
package io.micronaut.context.python.netty;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.context.python.PythonAsyncioRuntime;
import io.micronaut.context.python.PythonContextRuntime;
import io.micronaut.context.python.PythonEventLoop;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.channel.NettyChannelType;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.EventLoop;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.resolver.AddressResolverGroup;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ScopedValue.CallableOp;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Python event loop backed by a Netty {@link EventLoop}.
 *
 * <p>This class is intentionally a narrow bridge between Python's asyncio transport
 * protocol APIs and Micronaut's Netty event loop. It does not try to expose raw
 * Netty channels to Python callers. The public nested transport/server/facade types
 * are GraalPy host objects whose method names are part of the Python-facing surface,
 * including snake_case names that mirror asyncio. Do not rename those methods to
 * satisfy Java style checks unless the Python shims and tests are updated at the
 * same time.</p>
 *
 * <p>All callbacks into Python protocols are made from the Netty event loop thread.
 * When this loop is entered from a different Java thread, work is rescheduled onto
 * Netty before touching channel state or invoking Python callbacks.</p>
 */
@Internal
@Experimental
public final class NettyPythonEventLoop implements PythonEventLoop {
    /** Parsed once per context; {@code bytes} is looked up when a handler is created, not per packet. */
    private static final Logger LOG = LoggerFactory.getLogger(NettyPythonEventLoop.class);
    private static final String TRANSPORT_WRAPPER = "__micronaut_netty_transport";
    private static final String DATAGRAM_TRANSPORT_WRAPPER = "__micronaut_netty_datagram_transport";
    private static final String JAVA_TRANSPORT_MEMBER = "_java";
    private static final Source BYTES_SOURCE = Source.newBuilder(PythonContextRuntime.PYTHON, "bytes", "micronaut-bytes.py").cached(true).buildLiteral();

    private final EventLoop eventLoop;
    private final NettyPythonEventLoopSupport support;

    /**
     * @param eventLoop The Netty event loop.
     */
    public NettyPythonEventLoop(EventLoop eventLoop) {
        this(eventLoop, new NettyPythonEventLoopSupport());
    }

    NettyPythonEventLoop(EventLoop eventLoop, NettyPythonEventLoopSupport support) {
        this.eventLoop = Objects.requireNonNull(eventLoop, "eventLoop");
        this.support = Objects.requireNonNull(support, "support");
    }

    @Override
    public boolean inEventLoop() {
        return eventLoop.inEventLoop();
    }

    @Override
    public void execute(Runnable runnable) {
        eventLoop.execute(bound(runnable));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
        return eventLoop.schedule(bound(runnable), delay, unit);
    }

    @Override
    public void executeCallback(Value callback) {
        eventLoop.execute(bound(guestCallback(callback)));
    }

    @Override
    public ScheduledFuture<?> scheduleCallback(Value callback, long delay, TimeUnit unit) {
        return eventLoop.schedule(bound(guestCallback(callback)), delay, unit);
    }

    /**
     * A {@code call_soon}/{@code call_later} callback may outlive the coroutine that scheduled it;
     * it runs inside an execution frame of its context so shutdown waits for it, and it is skipped
     * once the context has been unregistered for closing.
     */
    private static Runnable guestCallback(Value callback) {
        Context context = callback.getContext();
        return () -> {
            if (!PythonContextRuntime.tryWithExecutionFrame(context, callback::executeVoid)) {
                LOG.debug("Skipping an asyncio callback scheduled on a Python context that is closing");
            }
        };
    }

    @Override
    public double time() {
        return System.nanoTime() / 1_000_000_000.0d;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof NettyPythonEventLoop other && eventLoop == other.eventLoop && support == other.support;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(eventLoop) * 31 + System.identityHashCode(support);
    }

    /**
     * Returns the underlying Netty event loop.
     *
     * @return The underlying Netty event loop.
     */
    public EventLoop eventLoop() {
        return eventLoop;
    }

    /**
     * Create a Netty-backed asyncio TCP connection.
     *
     * @param protocolFactory The Python protocol factory.
     * @param host The remote host.
     * @param port The remote port.
     * @param localHost The local host, or null.
     * @param localPort The local port, or {@code -1}.
     * @param ssl TLS options.
     * @param serverHostname The TLS server hostname.
     * @param sslHandshakeTimeout The TLS handshake timeout in seconds.
     * @param sslShutdownTimeout The TLS shutdown timeout in seconds.
     * @return A stage completing with transport and protocol.
     */
    public CompletionStage<Object[]> createConnection(Value protocolFactory,
                                                      String host,
                                                      int port,
                                                      @Nullable String localHost,
                                                      int localPort,
                                                      @Nullable Object ssl,
                                                      @Nullable String serverHostname,
                                                      @Nullable Double sslHandshakeTimeout,
                                                      @Nullable Double sslShutdownTimeout) {
        return createConnection(protocolFactory, host, port, localHost, localPort, ssl, serverHostname, sslHandshakeTimeout, sslShutdownTimeout, "");
    }

    /**
     * Connect a TCP client, resolving the host to an address of the requested family.
     *
     * @param protocolFactory The asyncio protocol factory.
     * @param host The remote host.
     * @param port The remote port.
     * @param localHost The local host to bind, or null.
     * @param localPort The local port to bind, or -1.
     * @param ssl TLS options, or null.
     * @param serverHostname The TLS server name, or null.
     * @param sslHandshakeTimeout The TLS handshake timeout in seconds.
     * @param sslShutdownTimeout The TLS shutdown timeout in seconds.
     * @param family {@code inet}, {@code inet6} or empty for any address family
     * @return A stage completing with transport and protocol.
     */
    public CompletionStage<Object[]> createConnection(Value protocolFactory,
                                                      String host,
                                                      int port,
                                                      @Nullable String localHost,
                                                      int localPort,
                                                      @Nullable Object ssl,
                                                      @Nullable String serverHostname,
                                                      @Nullable Double sslHandshakeTimeout,
                                                      @Nullable Double sslShutdownTimeout,
                                                      String family) {
        CompletableFuture<Object[]> future = new CompletableFuture<>();
        Runnable operationDone;
        try {
            // shutdown refuses new operations and waits for this one's channel, even if it is created later
            operationDone = support.beginOperation();
        } catch (IllegalStateException e) {
            future.completeExceptionally(e);
            return future;
        }
        future.whenComplete((ignored, ignoredFailure) -> operationDone.run());
        Runnable connect = () -> {
            try {
                NettyPythonEventLoopSupport.TlsOptions tlsOptions = support.tlsOptions(
                    ssl,
                    false,
                    serverHostname == null && ssl != null ? host : serverHostname,
                    port,
                    sslHandshakeTimeout,
                    sslShutdownTimeout
                );
                Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoop)
                    .resolver(resolver())
                    .channel(support.channelClass(eventLoop, NettyChannelType.CLIENT_SOCKET))
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            support.track(channel);
                            closeOnFailure(future, channel);
                            if (tlsOptions != null) {
                                channel.pipeline().addLast(support.sslHandler(channel, tlsOptions));
                            }
                            channel.pipeline().addLast(new NettySocketHandler(NettyPythonEventLoop.this, protocolFactory, future, tlsOptions != null));
                        }
                    });
                InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved(host, port);
                InetSocketAddress localAddress = toUnresolvedSocketAddress(localHost, localPort);
                resolveOptionalAddresses(localAddress, family, future, resolvedLocals -> resolveAddresses(remoteAddress, family, future, resolvedRemotes ->
                    connectSequentially(bootstrap, resolvedRemotes, resolvedLocals, future, new ArrayList<>())));
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (eventLoop.inEventLoop()) {
            connect.run();
        } else {
            execute(connect);
        }
        return future;
    }

    /**
     * Create a Netty-backed asyncio TCP server.
     *
     * @param protocolFactory The Python protocol factory.
     * @param host The local host.
     * @param port The local port.
     * @param backlog The listen backlog.
     * @param reuseAddress Whether address reuse is enabled.
     * @param reusePort Whether port reuse is requested.
     * @param startServing Whether to start accepting connections immediately.
     * @param ssl TLS options.
     * @param sslHandshakeTimeout The TLS handshake timeout in seconds.
     * @param sslShutdownTimeout The TLS shutdown timeout in seconds.
     * @return A stage completing with the server.
     */
    public CompletionStage<NettyServer> createServer(Value protocolFactory,
                                                     @Nullable String host,
                                                     int port,
                                                     int backlog,
                                                     boolean reuseAddress,
                                                     boolean reusePort,
                                                     boolean startServing,
                                                     @Nullable Object ssl,
                                                     @Nullable Double sslHandshakeTimeout,
                                                     @Nullable Double sslShutdownTimeout) {
        return createServer(protocolFactory, host, port, backlog, reuseAddress, reusePort, startServing, ssl, sslHandshakeTimeout, sslShutdownTimeout, "");
    }

    /**
     * Bind a TCP server on an address of the requested family.
     *
     * @param protocolFactory The asyncio protocol factory.
     * @param host The host to bind, or null for every address.
     * @param port The port to bind.
     * @param backlog The accept backlog.
     * @param reuseAddress Whether to set {@code SO_REUSEADDR}.
     * @param reusePort Whether to set {@code SO_REUSEPORT}.
     * @param startServing Whether to accept connections right away.
     * @param ssl TLS options, or null.
     * @param sslHandshakeTimeout The TLS handshake timeout in seconds.
     * @param sslShutdownTimeout The TLS shutdown timeout in seconds.
     * @param family {@code inet}, {@code inet6} or empty for any address family
     * @return A stage completing with the server.
     */
    public CompletionStage<NettyServer> createServer(Value protocolFactory,
                                                     @Nullable String host,
                                                     int port,
                                                     int backlog,
                                                     boolean reuseAddress,
                                                     boolean reusePort,
                                                     boolean startServing,
                                                     @Nullable Object ssl,
                                                     @Nullable Double sslHandshakeTimeout,
                                                     @Nullable Double sslShutdownTimeout,
                                                     String family) {
        CompletableFuture<NettyServer> future = new CompletableFuture<>();
        Runnable operationDone;
        try {
            // shutdown refuses new operations and waits for this one's channel, even if it is created later
            operationDone = support.beginOperation();
        } catch (IllegalStateException e) {
            future.completeExceptionally(e);
            return future;
        }
        future.whenComplete((ignored, ignoredFailure) -> operationDone.run());
        Runnable bind = () -> {
            try {
                NettyPythonEventLoopSupport.TlsOptions tlsOptions = support.tlsOptions(ssl, true, null, port, sslHandshakeTimeout, sslShutdownTimeout);
                AcceptedClients clients = new AcceptedClients();
                // the channel class is chosen per bind: the transport's default for a host, one per family for the wildcards
                ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(eventLoop, eventLoop)
                    .option(ChannelOption.SO_BACKLOG, backlog)
                    // start_serving=False: no accept before start_serving(), not merely after the facade exists
                    .option(ChannelOption.AUTO_READ, startServing)
                    .option(ChannelOption.SO_REUSEADDR, reuseAddress)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            support.track(channel);
                            clients.add(channel);
                            if (tlsOptions != null) {
                                channel.pipeline().addLast(support.sslHandler(channel, tlsOptions));
                            }
                            channel.pipeline().addLast(new NettySocketHandler(NettyPythonEventLoop.this, protocolFactory, null, tlsOptions != null));
                        }
                    });
                if (reusePort) {
                    ChannelOption<Boolean> reusePortOption = support.reusePortOption(eventLoop);
                    if (reusePortOption == null) {
                        throw new UnsupportedOperationException("reuse_port requires a Netty native transport that supports SO_REUSEPORT");
                    }
                    bootstrap.option(reusePortOption, true);
                }
                InetSocketAddress bindAddress;
                if (host == null && family.isEmpty()) {
                    // asyncio's "all interfaces": one listener. The JDK and Netty's native transports
                    // create IPv6 sockets with IPV6_V6ONLY cleared, so the IPv6 wildcard listener is
                    // dual-stack and serves IPv4 too; a JVM without IPv6 sockets gets the IPv4 wildcard.
                    // Any bind failure, a port in use included, fails the call.
                    String wildcardFamily = NettyPythonEventLoopSupport.ipv6Available() ? "inet6" : "inet";
                    bootstrap.channelFactory(() -> (ServerChannel) support.newServerChannel(eventLoop, wildcardFamily));
                    bindAddress = wildcardAddress(port, wildcardFamily);
                } else {
                    bootstrap.channel(serverChannelClass(NettyChannelType.SERVER_SOCKET));
                    bindAddress = host == null ? wildcardAddress(port, family) : InetSocketAddress.createUnresolved(host, port);
                }
                // asyncio binds one listening socket per resolved address of the host
                resolveAddresses(bindAddress, family, future, resolvedBinds -> bindSequentially(bootstrap, resolvedBinds, new ArrayList<>(), future, clients, startServing));
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (eventLoop.inEventLoop()) {
            bind.run();
        } else {
            execute(bind);
        }
        return future;
    }

    /**
     * Wrap a Netty channel accepted elsewhere as an asyncio TCP transport.
     *
     * @param protocolFactory The Python protocol factory.
     * @param socket The accepted Netty channel or transport exposing one.
     * @param ssl TLS options.
     * @param sslHandshakeTimeout The TLS handshake timeout in seconds.
     * @param sslShutdownTimeout The TLS shutdown timeout in seconds.
     * @return A stage completing with transport and protocol.
     */
    public CompletionStage<Object[]> connectAcceptedSocket(Value protocolFactory,
                                                           Object socket,
                                                           @Nullable Object ssl,
                                                           @Nullable Double sslHandshakeTimeout,
                                                           @Nullable Double sslShutdownTimeout) {
        CompletableFuture<Object[]> future = new CompletableFuture<>();
        Runnable operationDone;
        try {
            // shutdown refuses new operations and waits for this one's channel, even if it is created later
            operationDone = support.beginOperation();
        } catch (IllegalStateException e) {
            future.completeExceptionally(e);
            return future;
        }
        future.whenComplete((ignored, ignoredFailure) -> operationDone.run());
        Runnable connect = () -> {
            try {
                Channel channel = toChannel(socket);
                if (channel == null) {
                    future.complete(null);
                    return;
                }
                if (channel.eventLoop() != eventLoop) {
                    // the pipeline and the protocol callbacks belong to the channel's own loop
                    throw new IllegalArgumentException("The accepted channel belongs to another event loop; adopt it from the loop that accepted it");
                }
                support.track(channel);
                closeOnFailure(future, channel);
                NettyPythonEventLoopSupport.TlsOptions tlsOptions = support.tlsOptions(ssl, true, null, -1, sslHandshakeTimeout, sslShutdownTimeout);
                if (tlsOptions != null) {
                    channel.pipeline().addLast(support.sslHandler(channel, tlsOptions));
                }
                NettySocketHandler handler = new NettySocketHandler(this, protocolFactory, future, tlsOptions != null);
                channel.pipeline().addLast(handler);
                if (channel.isActive() && tlsOptions == null) {
                    handler.activate(channel);
                }
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (eventLoop.inEventLoop()) {
            connect.run();
        } else {
            execute(connect);
        }
        return future;
    }

    /**
     * Create a Netty-backed asyncio Unix-domain socket connection.
     *
     * @param protocolFactory The Python protocol factory.
     * @param path The Unix-domain socket path.
     * @param ssl TLS options.
     * @param serverHostname The TLS server hostname.
     * @param sslHandshakeTimeout The TLS handshake timeout in seconds.
     * @param sslShutdownTimeout The TLS shutdown timeout in seconds.
     * @return A stage completing with transport and protocol.
     */
    public CompletionStage<Object[]> createUnixConnection(Value protocolFactory,
                                                          String path,
                                                          @Nullable Object ssl,
                                                          @Nullable String serverHostname,
                                                          @Nullable Double sslHandshakeTimeout,
                                                          @Nullable Double sslShutdownTimeout) {
        CompletableFuture<Object[]> future = new CompletableFuture<>();
        Runnable operationDone;
        try {
            // shutdown refuses new operations and waits for this one's channel, even if it is created later
            operationDone = support.beginOperation();
        } catch (IllegalStateException e) {
            future.completeExceptionally(e);
            return future;
        }
        future.whenComplete((ignored, ignoredFailure) -> operationDone.run());
        Runnable connect = () -> {
            try {
                NettyPythonEventLoopSupport.TlsOptions tlsOptions = support.tlsOptions(ssl, false, serverHostname, -1, sslHandshakeTimeout, sslShutdownTimeout);
                Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoop)
                    .channel(support.channelClass(eventLoop, NettyChannelType.DOMAIN_SOCKET))
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            support.track(channel);
                            closeOnFailure(future, channel);
                            if (tlsOptions != null) {
                                channel.pipeline().addLast(support.sslHandler(channel, tlsOptions));
                            }
                            channel.pipeline().addLast(new NettySocketHandler(NettyPythonEventLoop.this, protocolFactory, future, tlsOptions != null));
                        }
                    });
                bootstrap.connect(support.domainSocketAddress(eventLoop, path)).addListener(closeOnFailure(future));
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (eventLoop.inEventLoop()) {
            connect.run();
        } else {
            execute(connect);
        }
        return future;
    }

    /**
     * Create a Netty-backed asyncio Unix-domain socket server.
     *
     * @param protocolFactory The Python protocol factory.
     * @param path The Unix-domain socket path.
     * @param backlog The listen backlog.
     * @param startServing Whether to start accepting connections immediately.
     * @param ssl TLS options.
     * @param sslHandshakeTimeout The TLS handshake timeout in seconds.
     * @param sslShutdownTimeout The TLS shutdown timeout in seconds.
     * @return A stage completing with the server.
     */
    public CompletionStage<NettyServer> createUnixServer(Value protocolFactory,
                                                         String path,
                                                         int backlog,
                                                         boolean startServing,
                                                         @Nullable Object ssl,
                                                         @Nullable Double sslHandshakeTimeout,
                                                         @Nullable Double sslShutdownTimeout) {
        CompletableFuture<NettyServer> future = new CompletableFuture<>();
        Runnable operationDone;
        try {
            // shutdown refuses new operations and waits for this one's channel, even if it is created later
            operationDone = support.beginOperation();
        } catch (IllegalStateException e) {
            future.completeExceptionally(e);
            return future;
        }
        future.whenComplete((ignored, ignoredFailure) -> operationDone.run());
        Runnable bind = () -> {
            try {
                NettyPythonEventLoopSupport.TlsOptions tlsOptions = support.tlsOptions(ssl, true, null, -1, sslHandshakeTimeout, sslShutdownTimeout);
                AcceptedClients clients = new AcceptedClients();
                ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(eventLoop, eventLoop)
                    .channel(serverChannelClass(NettyChannelType.DOMAIN_SERVER_SOCKET))
                    .option(ChannelOption.SO_BACKLOG, backlog)
                    .option(ChannelOption.AUTO_READ, startServing)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            support.track(channel);
                            clients.add(channel);
                            if (tlsOptions != null) {
                                channel.pipeline().addLast(support.sslHandler(channel, tlsOptions));
                            }
                            channel.pipeline().addLast(new NettySocketHandler(NettyPythonEventLoop.this, protocolFactory, null, tlsOptions != null));
                        }
                    });
                bootstrap.bind(support.domainSocketAddress(eventLoop, path)).addListener((ChannelFutureListener) bindFuture -> {
                    if (bindFuture.isSuccess()) {
                        support.track(bindFuture.channel());
                        closeOnFailure(future, bindFuture.channel());
                        future.complete(new NettyServer(List.of(bindFuture.channel()), clients, startServing));
                    } else {
                        future.completeExceptionally(bindFuture.cause());
                    }
                });
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (eventLoop.inEventLoop()) {
            bind.run();
        } else {
            execute(bind);
        }
        return future;
    }

    /**
     * Create a Netty-backed asyncio datagram endpoint.
     *
     * @param protocolFactory The Python protocol factory.
     * @param localHost The local host, or null.
     * @param localPort The local port, or {@code -1}.
     * @param remoteHost The remote host, or null.
     * @param remotePort The remote port, or {@code -1}.
     * @param allowBroadcast Whether broadcast is enabled.
     * @param reusePort Whether port reuse is requested.
     * @return A stage completing with transport and protocol.
     */
    public CompletionStage<Object[]> createDatagramEndpoint(Value protocolFactory,
                                                            @Nullable String localHost,
                                                            int localPort,
                                                            @Nullable String remoteHost,
                                                            int remotePort,
                                                            boolean allowBroadcast,
                                                            boolean reusePort) {
        return createDatagramEndpoint(protocolFactory, localHost, localPort, remoteHost, remotePort, allowBroadcast, reusePort, "");
    }

    /**
     * Open a datagram endpoint on an address of the requested family.
     *
     * @param protocolFactory The asyncio datagram protocol factory.
     * @param localHost The local host, or null.
     * @param localPort The local port, or -1.
     * @param remoteHost The remote host to connect to, or null.
     * @param remotePort The remote port, or -1.
     * @param allowBroadcast Whether to set {@code SO_BROADCAST}.
     * @param reusePort Whether to set {@code SO_REUSEPORT}.
     * @param family {@code inet}, {@code inet6} or empty for any address family
     * @return A stage completing with transport and protocol.
     */
    public CompletionStage<Object[]> createDatagramEndpoint(Value protocolFactory,
                                                            @Nullable String localHost,
                                                            int localPort,
                                                            @Nullable String remoteHost,
                                                            int remotePort,
                                                            boolean allowBroadcast,
                                                            boolean reusePort,
                                                            String family) {
        CompletableFuture<Object[]> future = new CompletableFuture<>();
        Runnable operationDone;
        try {
            // shutdown refuses new operations and waits for this one's channel, even if it is created later
            operationDone = support.beginOperation();
        } catch (IllegalStateException e) {
            future.completeExceptionally(e);
            return future;
        }
        future.whenComplete((ignored, ignoredFailure) -> operationDone.run());
        Runnable bind = () -> {
            try {
                InetSocketAddress local = toUnresolvedSocketAddress(localHost, localPort);
                InetSocketAddress remote = toUnresolvedSocketAddress(remoteHost, remotePort);
                Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoop)
                    .resolver(resolver())
                    .channelFactory(() -> support.newDatagramChannel(eventLoop, family))
                    .option(ChannelOption.SO_BROADCAST, allowBroadcast)
                    // no reads until the protocol exists: asyncio creates it after bind and connect
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new NettyDatagramHandler(this));
                if (reusePort) {
                    ChannelOption<Boolean> reusePortOption = support.reusePortOption(eventLoop);
                    if (reusePortOption == null) {
                        throw new UnsupportedOperationException("reuse_port requires a Netty native transport that supports SO_REUSEPORT");
                    }
                    bootstrap.option(reusePortOption, true);
                }
                // asyncio pairs the resolved local and remote addresses by family and tries each pair in turn
                resolveOptionalAddresses(local, family, future, resolvedLocals -> resolveOptionalAddresses(remote, family, future, resolvedRemotes -> {
                    List<InetSocketAddress[]> pairs = new ArrayList<>();
                    if (resolvedRemotes.isEmpty()) {
                        resolvedLocals.forEach(candidate -> pairs.add(new InetSocketAddress[] {candidate, null}));
                    } else {
                        for (InetSocketAddress resolvedRemote : resolvedRemotes) {
                            if (resolvedLocals.isEmpty()) {
                                pairs.add(new InetSocketAddress[] {new InetSocketAddress(wildcardOf(resolvedRemote), 0), resolvedRemote});
                            } else {
                                resolvedLocals.stream().filter(candidate -> sameFamily(candidate, resolvedRemote))
                                    .forEach(candidate -> pairs.add(new InetSocketAddress[] {candidate, resolvedRemote}));
                            }
                        }
                    }
                    if (pairs.isEmpty() && resolvedLocals.isEmpty() && resolvedRemotes.isEmpty()) {
                        // create_datagram_endpoint(factory, family=AF_INET): a wildcard socket of that family
                        pairs.add(new InetSocketAddress[] {wildcardAddress(0, family), null});
                    }
                    if (pairs.isEmpty()) {
                        future.completeExceptionally(new java.net.SocketException("No local and remote address of the same family"));
                        return;
                    }
                    bindDatagramPairs(bootstrap, protocolFactory, pairs, 0, new ArrayList<>(), future);
                }));
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (eventLoop.inEventLoop()) {
            bind.run();
        } else {
            execute(bind);
        }
        return future;
    }

    private static InetAddress wildcardOf(InetSocketAddress remote) {
        try {
            return remote.getAddress() instanceof Inet6Address ? InetAddress.getByName("::") : InetAddress.getByName("0.0.0.0");
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Bind, and connect when a remote is given, each candidate pair in turn until one succeeds. */
    private void bindDatagramPairs(Bootstrap bootstrap,
                                   Value protocolFactory,
                                   List<InetSocketAddress[]> pairs,
                                   int index,
                                   List<Throwable> failures,
                                   CompletableFuture<Object[]> future) {
        if (future.isDone()) {
            return;
        }
        if (index == pairs.size()) {
            Throwable last = failures.getLast();
            failures.subList(0, failures.size() - 1).forEach(last::addSuppressed);
            future.completeExceptionally(last);
            return;
        }
        InetSocketAddress bindAddress = pairs.get(index)[0];
        InetSocketAddress remote = pairs.get(index)[1];
        Runnable next = () -> bindDatagramPairs(bootstrap, protocolFactory, pairs, index + 1, failures, future);
        bootstrap.bind(bindAddress).addListener((ChannelFutureListener) bindFuture -> {
            if (!bindFuture.isSuccess()) {
                failures.add(bindFuture.cause());
                next.run();
                return;
            }
            Channel channel = bindFuture.channel();
            support.track(channel);
            if (remote == null) {
                closeOnFailure(future, channel);
                finishDatagramConnect(protocolFactory, future, channel, null);
                return;
            }
            channel.connect(remote).addListener((ChannelFutureListener) connectFuture -> {
                if (connectFuture.isSuccess()) {
                    closeOnFailure(future, channel);
                    finishDatagramConnect(protocolFactory, future, channel, remote);
                } else {
                    channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                    failures.add(connectFuture.cause());
                    next.run();
                }
            });
        });
    }

    private void finishDatagramConnect(Value protocolFactory,
                                       CompletableFuture<Object[]> future,
                                       Channel channel,
                                       @Nullable InetSocketAddress remote) {
        if (future.isCancelled()) {
            channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            return;
        }
        try {
            // the endpoint exists now: one frame creates the protocol and its helpers, hands it the
            // transport and then starts reading
            Object[] result = guest(this, protocolFactory, () -> {
                Value protocol = protocolFactory.execute();
                NettyDatagramTransport transport = new NettyDatagramTransport(this, channel, protocol, remote, eventLoop, support);
                channel.pipeline().get(NettyDatagramHandler.class).protocol(protocol, pythonBytesType(protocol.getContext()));
                Value pythonTransport = pythonTransport(protocol, DATAGRAM_TRANSPORT_WRAPPER).execute(transport);
                protocol.invokeMember("connection_made", pythonTransport);
                return new Object[] {pythonTransport, protocol};
            });
            channel.config().setAutoRead(true);
            channel.read();
            future.complete(result);
        } catch (Throwable e) {
            channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            future.completeExceptionally(e);
        }
    }

    /**
     * Connect to the resolved addresses one after the other, the way asyncio does without happy
     * eyeballs: the first that accepts wins, and when none does the last failure carries the others
     * as suppressed exceptions so {@code all_errors=True} can report every attempt.
     */
    private void connectSequentially(Bootstrap bootstrap,
                                     List<InetSocketAddress> addresses,
                                     List<InetSocketAddress> localAddresses,
                                     CompletableFuture<Object[]> future,
                                     List<Throwable> failures) {
        if (future.isDone()) {
            return;
        }
        if (failures.size() == addresses.size()) {
            Throwable last = failures.getLast();
            failures.subList(0, failures.size() - 1).forEach(last::addSuppressed);
            future.completeExceptionally(last);
            return;
        }
        InetSocketAddress remote = addresses.get(failures.size());
        // every local address of the remote's family is tried in turn, as asyncio pairs getaddrinfo results
        List<InetSocketAddress> locals = localAddresses.stream()
            .filter(candidate -> sameFamily(candidate, remote))
            .toList();
        if (!localAddresses.isEmpty() && locals.isEmpty()) {
            failures.add(new java.net.SocketException("No local address of the family of " + remote.getAddress().getHostAddress()));
            connectSequentially(bootstrap, addresses, localAddresses, future, failures);
            return;
        }
        connectPairs(bootstrap, remote, locals, 0, new ArrayList<>(), pairFailure -> {
            failures.add(pairFailure);
            connectSequentially(bootstrap, addresses, localAddresses, future, failures);
        });
    }

    private static boolean sameFamily(InetSocketAddress first, InetSocketAddress second) {
        return first.getAddress().getClass() == second.getAddress().getClass();
    }

    /** One remote address with each of its local candidates; the last failure carries the others. */
    private void connectPairs(Bootstrap bootstrap,
                              InetSocketAddress remote,
                              List<InetSocketAddress> locals,
                              int index,
                              List<Throwable> failures,
                              Consumer<Throwable> onFailure) {
        if (!failures.isEmpty() && (locals.isEmpty() || index == locals.size())) {
            Throwable last = failures.getLast();
            failures.subList(0, failures.size() - 1).forEach(last::addSuppressed);
            onFailure.accept(last);
            return;
        }
        ChannelFuture connectFuture = locals.isEmpty() ? bootstrap.connect(remote) : bootstrap.connect(remote, locals.get(index));
        connectFuture.addListener((ChannelFutureListener) attempt -> {
            if (attempt.isSuccess()) {
                return;
            }
            attempt.channel().close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            failures.add(attempt.cause());
            connectPairs(bootstrap, remote, locals, index + 1, failures, onFailure);
        });
    }

    /**
     * Bind a listening socket on each resolved address; a failure closes what was bound already.
     */
    private void bindSequentially(ServerBootstrap bootstrap,
                                  List<InetSocketAddress> addresses,
                                  List<Channel> bound,
                                  CompletableFuture<NettyServer> future,
                                  AcceptedClients clients,
                                  boolean startServing) {
        if (future.isCancelled()) {
            bound.forEach(channel -> channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
            return;
        }
        if (bound.size() == addresses.size()) {
            future.complete(new NettyServer(List.copyOf(bound), clients, startServing));
            return;
        }
        bootstrap.bind(addresses.get(bound.size())).addListener((ChannelFutureListener) bindFuture -> {
            if (bindFuture.isSuccess()) {
                support.track(bindFuture.channel());
                closeOnFailure(future, bindFuture.channel());
                bound.add(bindFuture.channel());
                bindSequentially(bootstrap, addresses, bound, future, clients, startServing);
            } else {
                future.completeExceptionally(bindFuture.cause());
            }
        });
    }

    private void resolveOptionalAddresses(@Nullable InetSocketAddress address,
                                          String family,
                                          CompletableFuture<?> future,
                                          Consumer<List<InetSocketAddress>> consumer) {
        if (address == null) {
            consumer.accept(List.of());
            return;
        }
        resolveAddresses(address, family, future, consumer);
    }

    private void resolveAddress(@Nullable InetSocketAddress address,
                                String family,
                                CompletableFuture<?> future,
                                Consumer<@Nullable InetSocketAddress> consumer) {
        if (address == null) {
            consumer.accept(null);
            return;
        }
        resolveAddresses(address, family, future, resolved -> consumer.accept(resolved.getFirst()));
    }

    /**
     * Resolve a host to every address of the requested family, in resolver order; a literal is
     * checked against the family without a lookup.
     */
    private void resolveAddresses(InetSocketAddress address,
                                  String family,
                                  CompletableFuture<?> future,
                                  Consumer<List<InetSocketAddress>> consumer) {
        if (address.isUnresolved() && address.getHostString().indexOf(':') >= 0) {
            // an IPv6 literal, with its scope when it has one: no lookup, and the scope survives
            try {
                resolveAddresses(new InetSocketAddress(InetAddress.getByName(address.getHostString()), address.getPort()), family, future, consumer);
            } catch (UnknownHostException e) {
                future.completeExceptionally(e);
            }
            return;
        }
        if (!address.isUnresolved()) {
            if (!matchesFamily(address, family)) {
                future.completeExceptionally(new UnknownHostException(address.getHostString() + " is not an " + family + " address"));
                return;
            }
            consumer.accept(List.of(address));
            return;
        }
        // asyncio's family argument narrows the lookup to the resolved addresses of that family
        support.resolver(eventLoop).getResolver(eventLoop).resolveAll(address).addListener(resolveFuture -> {
            if (future.isCancelled()) {
                return;
            }
            if (!resolveFuture.isSuccess()) {
                future.completeExceptionally(resolveFuture.cause());
                return;
            }
            List<InetSocketAddress> resolved = new ArrayList<>();
            for (Object candidate : (List<?>) resolveFuture.getNow()) {
                if (candidate instanceof InetSocketAddress inetSocketAddress && matchesFamily(inetSocketAddress, family)) {
                    resolved.add(inetSocketAddress);
                }
            }
            if (resolved.isEmpty()) {
                future.completeExceptionally(new UnknownHostException("No " + (family.isEmpty() ? "" : family + " ") + "address found for " + address.getHostString()));
                return;
            }
            consumer.accept(resolved);
        });
    }

    private static ChannelFutureListener closeOnFailure(CompletableFuture<?> future) {
        return connectFuture -> {
            if (!connectFuture.isSuccess()) {
                connectFuture.channel().close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                future.completeExceptionally(connectFuture.cause());
            }
        };
    }

    /**
     * A channel is only useful to Python once its future succeeds: close it when the future is
     * cancelled or fails (a protocol factory that throws, TLS setup, a remote that does not resolve).
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    private static void closeOnFailure(CompletableFuture<?> future, Channel channel) {
        future.whenComplete((ignored, throwable) -> {
            if (throwable != null && channel.isOpen()) {
                channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private AddressResolverGroup<SocketAddress> resolver() {
        return (AddressResolverGroup) support.resolver(eventLoop);
    }

    private Class<? extends ServerChannel> serverChannelClass(NettyChannelType type) {
        return support.channelClass(eventLoop, type).asSubclass(ServerChannel.class);
    }

    private Runnable bound(Runnable runnable) {
        return () -> NettyPythonEventLoopProvider.bindLoop(this, runnable);
    }

    /**
     * Run guest code from a Netty callback: bound to this loop (the one that opened the channel, so
     * nested bridge calls route to it and its provider tracks what they open) and inside an
     * execution frame of the guest's context so shutdown waits for it.
     */
    private static <T, X extends Throwable> T guest(NettyPythonEventLoop loop, Value guestValue, CallableOp<T, X> operation) throws X {
        // a tracked frame: a channel outliving its Python context must not revive the context's state
        return NettyPythonEventLoopProvider.bindLoop(loop, () -> PythonContextRuntime.withTrackedExecutionFrame(guestValue.getContext(), operation));
    }

    private static void guestRun(NettyPythonEventLoop loop, Value guestValue, Runnable action) {
        guest(loop, guestValue, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Whether an address belongs to the family asyncio asked for: {@code inet}, {@code inet6} or empty for any.
     */
    private static boolean matchesFamily(InetSocketAddress address, String family) {
        return switch (family) {
            case "inet" -> address.getAddress() instanceof Inet4Address;
            case "inet6" -> address.getAddress() instanceof Inet6Address;
            default -> true;
        };
    }

    private static InetSocketAddress wildcardAddress(int port, String family) {
        try {
            return switch (family) {
                case "inet" -> new InetSocketAddress(InetAddress.getByName("0.0.0.0"), port);
                case "inet6" -> new InetSocketAddress(InetAddress.getByName("::"), port);
                default -> new InetSocketAddress(port);
            };
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * Python asyncio datagram addresses use tuple-like values, while Netty uses
     * SocketAddress variants. Keep conversion small and explicit so unsupported
     * address shapes fail before Netty writes a packet to an unintended target.
     */
    private static @Nullable InetSocketAddress toSocketAddress(@Nullable Object address) {
        if (address == null) {
            return null;
        }
        if (address instanceof InetSocketAddress socketAddress) {
            return socketAddress;
        }
        if (address instanceof Value value) {
            if (value.isNull()) {
                return null;
            }
            if (value.hasArrayElements() && value.getArraySize() >= 2) {
                int scopeId = value.getArraySize() >= 4 ? value.getArrayElement(3).asInt() : 0;
                return toSocketAddress(value.getArrayElement(0).asString(), value.getArrayElement(1).asInt(), scopeId);
            }
        }
        if (address instanceof Object[] values && values.length >= 2) {
            int scopeId = values.length >= 4 ? ((Number) values[3]).intValue() : 0;
            return toSocketAddress(values[0].toString(), ((Number) values[1]).intValue(), scopeId);
        }
        if (address instanceof List<?> values && values.size() >= 2) {
            // a Python tuple or list arrives as a list proxy, not as a Value
            int scopeId = values.size() >= 4 ? ((Number) values.get(3)).intValue() : 0;
            return toSocketAddress(values.get(0).toString(), ((Number) values.get(1)).intValue(), scopeId);
        }
        throw new IllegalArgumentException("Unsupported datagram address: " + address);
    }

    /**
     * An asyncio IPv6 address is {@code (host, port, flowinfo, scope_id)}: a scope on a literal
     * address is kept, a name goes to the resolver.
     */
    private static InetSocketAddress toSocketAddress(String host, int port, int scopeId) {
        if (scopeId != 0) {
            try {
                InetAddress literal = InetAddress.getByName(host);
                if (literal instanceof Inet6Address inet6Address) {
                    return new InetSocketAddress(Inet6Address.getByAddress(null, inet6Address.getAddress(), scopeId), port);
                }
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("A scope id needs an IPv6 literal, not " + host, e);
            }
        }
        return InetSocketAddress.createUnresolved(host, port);
    }

    private static @Nullable InetSocketAddress toUnresolvedSocketAddress(@Nullable String host, int port) {
        if (host == null || port < 0) {
            return null;
        }
        return InetSocketAddress.createUnresolved(host, port);
    }

    private static Object[] toPythonAddress(InetSocketAddress address) {
        // asyncio's shape: (host, port) for IPv4, (host, port, flowinfo, scope_id) for IPv6
        if (address.getAddress() instanceof Inet6Address inet6Address) {
            // the host without its "%scope": the scope is the fourth element, as asyncio has it
            String host;
            try {
                host = InetAddress.getByAddress(inet6Address.getAddress()).getHostAddress();
            } catch (UnknownHostException e) {
                host = inet6Address.getHostAddress();
            }
            return new Object[] {host, address.getPort(), 0, inet6Address.getScopeId()};
        }
        return new Object[] {address.getHostString(), address.getPort()};
    }

    /*
     * Python's transport.get_extra_info("sockname"/"peername") expects either a
     * (host, port) tuple-like value or a Unix-domain socket path. NIO domain
     * sockets use the JDK address type, while native epoll/kqueue channels use
     * Netty's DomainSocketAddress from netty-transport-native-unix-common.
     */
    private static @Nullable Object toPythonAddress(SocketAddress address) {
        if (address instanceof InetSocketAddress inetSocketAddress) {
            return toPythonAddress(inetSocketAddress);
        }
        if (address instanceof UnixDomainSocketAddress domainSocketAddress) {
            return domainSocketAddress.getPath().toString();
        }
        if (address instanceof DomainSocketAddress domainSocketAddress) {
            return domainSocketAddress.path();
        }
        return null;
    }

    private static Value pythonTransport(Value protocol, String wrapper) {
        return PythonAsyncioRuntime.asyncioHelper(protocol.getContext(), wrapper);
    }

    private static Value pythonBytesType(Context context) {
        return context.eval(BYTES_SOURCE);
    }

    private static @Nullable Channel toChannel(Object socket) {
        if (socket instanceof Channel channel) {
            return channel;
        }
        if (socket instanceof NettySocketTransport transport) {
            return transport.channel;
        }
        if (socket instanceof Value value) {
            if (value.isHostObject()) {
                Object hostObject = value.asHostObject();
                if (hostObject instanceof Channel channel) {
                    return channel;
                }
                if (hostObject instanceof NettySocketTransport transport) {
                    return transport.channel;
                }
            } else if (value.hasMember(JAVA_TRANSPORT_MEMBER)) {
                // the Python asyncio.Transport wrapper over a Java transport
                return toChannel(value.getMember(JAVA_TRANSPORT_MEMBER));
            }
        }
        return null;
    }

    private static final class NettySocketHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final NettyPythonEventLoop loop;
        private final Value protocolFactory;
        private final @Nullable CompletableFuture<Object[]> connectedFuture;
        private final boolean tls;
        /** Created when the connection is established, as asyncio does; null until then. */
        private @Nullable Value protocol;
        private @Nullable NettySocketTransport transport;
        private boolean connectionLost;
        private boolean writingPaused;

        /** The Python bytes type, resolved inside the activation frame. */
        private @Nullable Value bytesType;

        private NettySocketHandler(NettyPythonEventLoop loop, Value protocolFactory, @Nullable CompletableFuture<Object[]> connectedFuture, boolean tls) {
            this.loop = loop;
            this.protocolFactory = protocolFactory;
            this.connectedFuture = connectedFuture;
            this.tls = tls;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            if (!tls) {
                activate(ctx.channel());
            }
        }

        /*
         * For plain TCP, channelActive means asyncio can receive connection_made.
         * For TLS, activation is delayed until SslHandshakeCompletionEvent succeeds
         * so Python never sees an active transport before the secure session exists.
         */
        private void activate(Channel channel) {
            if (transport != null) {
                return;
            }
            try {
                // the connection exists: only now does asyncio call the protocol factory; one frame
                // covers the factory, the helpers and connection_made
                Object[] result = guest(loop, protocolFactory, () -> {
                    Value newProtocol = protocolFactory.execute();
                    protocol = newProtocol;
                    bytesType = pythonBytesType(newProtocol.getContext());
                    NettySocketTransport newTransport = new NettySocketTransport(channel, newProtocol, loop.support);
                    transport = newTransport;
                    // the protocol sees an asyncio.Transport: keyword arguments and isinstance checks work on it
                    Value pythonTransport = pythonTransport(newProtocol, TRANSPORT_WRAPPER).execute(newTransport);
                    newProtocol.invokeMember("connection_made", pythonTransport);
                    return new Object[] {pythonTransport, newProtocol};
                });
                if (connectedFuture != null) {
                    connectedFuture.complete(result);
                }
            } catch (Throwable e) {
                channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                if (connectedFuture != null) {
                    connectedFuture.completeExceptionally(e);
                }
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf content) {
            Value current = protocol;
            Value bytesFactory = bytesType;
            if (current == null || bytesFactory == null) {
                return;
            }
            byte[] bytes = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), bytes);
            // bytes(ByteBuffer) is one bulk copy; bytes(byte[]) reads the array element by element
            guestRun(loop, current, () -> current.invokeMember("data_received", bytesFactory.execute(ByteBuffer.wrap(bytes))));
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof SslHandshakeCompletionEvent handshake) {
                if (handshake.isSuccess()) {
                    activate(ctx.channel());
                } else {
                    if (connectedFuture != null) {
                        connectedFuture.completeExceptionally(handshake.cause());
                    }
                    ctx.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                }
                return;
            }
            super.userEventTriggered(ctx, evt);
        }

        /*
         * asyncio flow control: the protocol is told to pause writing when the queued bytes cross the
         * high-water mark and to resume once they drop below the low-water mark, once per transition.
         */
        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            Value current = protocol;
            if (current != null && transport != null && !connectionLost) {
                boolean writable = ctx.channel().isWritable();
                if (!writable && !writingPaused) {
                    writingPaused = true;
                    guestRun(loop, current, () -> current.invokeMember("pause_writing"));
                } else if (writable && writingPaused) {
                    writingPaused = false;
                    guestRun(loop, current, () -> current.invokeMember("resume_writing"));
                }
            }
            ctx.fireChannelWritabilityChanged();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            fireConnectionLost(ctx.channel(), null);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            try {
                fireConnectionLost(ctx.channel(), cause);
            } finally {
                // a connection_lost that throws must not keep the channel open
                ctx.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            }
        }

        private void fireConnectionLost(Channel channel, @Nullable Throwable cause) {
            Value current = protocol;
            if (connectionLost || current == null) {
                // a connection that never activated made no protocol, so there is nothing to tell
                return;
            }
            connectionLost = true;
            guestRun(loop, current, () -> current.invokeMember("connection_lost", cause));
        }
    }

    private static final class NettyDatagramHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final NettyPythonEventLoop loop;
        /** Created once the endpoint is bound and connected, as asyncio does; reads start then. */
        private volatile @Nullable Value protocol;
        private volatile @Nullable Value bytesType;
        private boolean connectionLost;

        private NettyDatagramHandler(NettyPythonEventLoop loop) {
            this.loop = loop;
        }

        void protocol(Value protocol, Value bytesType) {
            this.bytesType = bytesType;
            this.protocol = protocol;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            Value current = protocol;
            Value bytesFactory = bytesType;
            if (current == null || bytesFactory == null) {
                return;
            }
            ByteBuf content = packet.content();
            byte[] bytes = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), bytes);
            guestRun(loop, current, () -> current.invokeMember(
                "datagram_received",
                bytesFactory.execute(ByteBuffer.wrap(bytes)),
                toPythonAddress(packet.sender())
            ));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            Value current = protocol;
            if (current != null) {
                guestRun(loop, current, () -> current.invokeMember("error_received", cause));
            }
        }

        /** connection_lost once, whether the transport closed the channel or the provider did at shutdown. */
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            Value current = protocol;
            if (connectionLost || current == null) {
                return;
            }
            connectionLost = true;
            guestRun(loop, current, () -> current.invokeMember("connection_lost", (Object) null));
        }
    }

    /**
     * Netty-backed stream transport exposed to Python as a host object.
     *
     * <p>The snake_case methods are intentionally duplicated alongside Java-style
     * methods. GraalPy member lookup is name based, and asyncio's transports call
     * names such as {@code get_extra_info}, {@code pause_reading}, and
     * {@code is_closing}. The Checkstyle suppression below protects that
     * Python-facing contract.</p>
     */
    @SuppressWarnings({"EffectivelyPrivate", "UnusedMethod", "checkstyle:MethodName"})
    @Experimental
    public static final class NettySocketTransport {
        private final Channel channel;
        private final Value protocol;
        private final NettyPythonEventLoopSupport support;
        private volatile boolean closing;

        private NettySocketTransport(Channel channel, Value protocol, NettyPythonEventLoopSupport support) {
            this.channel = channel;
            this.protocol = protocol;
            this.support = support;
        }

        public void write(byte[] bytes) {
            if (closing) {
                return;
            }
            channel.writeAndFlush(Unpooled.wrappedBuffer(bytes)).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    // the socket handler reports connection_lost exactly once and closes the channel
                    channel.pipeline().fireExceptionCaught(future.cause());
                }
            });
        }

        public void writelines(Value lines) {
            if (!lines.hasArrayElements()) {
                return;
            }
            for (long i = 0; i < lines.getArraySize(); i++) {
                write(lines.getArrayElement(i).as(byte[].class));
            }
        }

        public boolean canWriteEof() {
            return channel.pipeline().get(SslHandler.class) == null;
        }

        public boolean can_write_eof() {
            return canWriteEof();
        }

        public void writeEof() {
            write_eof();
        }

        public void write_eof() {
            if (!canWriteEof()) {
                throw new UnsupportedOperationException("TLS transports do not support write_eof");
            }
            /*
             * asyncio write_eof maps to a half-close for stream sockets. Netty
             * only exposes shutdownOutput on SocketChannel; domain sockets and
             * other channel types are closed as the conservative fallback.
             */
            if (channel instanceof SocketChannel socketChannel) {
                socketChannel.shutdownOutput().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            } else {
                close();
            }
        }

        public int getWriteBufferSize() {
            // asyncio reports the bytes queued for writing, not the room left before the high-water mark
            ChannelOutboundBuffer outboundBuffer = channel.unsafe().outboundBuffer();
            return outboundBuffer == null ? 0 : (int) Math.min(Integer.MAX_VALUE, outboundBuffer.totalPendingWriteBytes());
        }

        public int get_write_buffer_size() {
            return getWriteBufferSize();
        }

        public Object[] getWriteBufferLimits() {
            WriteBufferWaterMark waterMark = channel.config().getWriteBufferWaterMark();
            return new Object[] {waterMark.low(), waterMark.high()};
        }

        public Object[] get_write_buffer_limits() {
            return getWriteBufferLimits();
        }

        public void setWriteBufferLimits(@Nullable Integer high, @Nullable Integer low) {
            set_write_buffer_limits(high, low);
        }

        public void set_write_buffer_limits(@Nullable Integer high, @Nullable Integer low) {
            // asyncio defaults: high 64 KiB (or four times low), low a quarter of high; both set at once
            int highWaterMark = high != null ? high : low != null ? 4 * low : 64 * 1024;
            int lowWaterMark = low != null ? low : highWaterMark / 4;
            if (highWaterMark < 0 || lowWaterMark < 0 || lowWaterMark > highWaterMark) {
                throw new IllegalArgumentException("high (" + highWaterMark + ") must be >= low (" + lowWaterMark + ") must be >= 0");
            }
            channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(lowWaterMark, highWaterMark));
        }

        public boolean isReading() {
            return !isClosing() && channel.config().isAutoRead();
        }

        public boolean is_reading() {
            return isReading();
        }

        public void pauseReading() {
            channel.config().setAutoRead(false);
        }

        public void pause_reading() {
            pauseReading();
        }

        public void resumeReading() {
            channel.config().setAutoRead(true);
            channel.read();
        }

        public void resume_reading() {
            resumeReading();
        }

        public @Nullable Object getExtraInfo(String name) {
            return getExtraInfo(name, null);
        }

        public @Nullable Object getExtraInfo(String name, @Nullable Object defaultValue) {
            /*
             * Keep this list intentionally small. Python code should get stable
             * asyncio-style metadata, not the mutable Netty Channel or pipeline.
             */
            return switch (name) {
                case "socket" -> new NettySocketFacade(channel);
                case "micronaut.netty" -> true;
                case "ssl_object" -> {
                    SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
                    yield sslHandler == null ? defaultValue : new NettySslFacade(sslHandler);
                }
                case "cipher" -> {
                    SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
                    yield sslHandler == null ? defaultValue : new NettySslFacade(sslHandler).cipher();
                }
                case "peercert" -> {
                    SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
                    yield sslHandler == null ? defaultValue : new NettySslFacade(sslHandler).getpeercert();
                }
                case "sockname" -> {
                    Object address = toPythonAddress(channel.localAddress());
                    yield address == null ? defaultValue : address;
                }
                case "peername" -> {
                    Object address = toPythonAddress(channel.remoteAddress());
                    yield address == null ? defaultValue : address;
                }
                default -> defaultValue;
            };
        }

        public @Nullable Object get_extra_info(String name) {
            return getExtraInfo(name);
        }

        public @Nullable Object get_extra_info(String name, @Nullable Object defaultValue) {
            return getExtraInfo(name, defaultValue);
        }

        public boolean isClosing() {
            // closed by the transport, by the peer, or by the provider whose shutdown has begun
            return closing || !channel.isOpen() || support.isClosed();
        }

        public boolean is_closing() {
            return isClosing();
        }

        public void close() {
            if (closing) {
                return;
            }
            closing = true;
            channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }

        public void abort() {
            close();
        }

        @SuppressWarnings("DoNotCallSuggester")
        public void setProtocol(Value protocol) {
            throw new UnsupportedOperationException("Changing the protocol of a Netty-backed Python transport is not supported");
        }

        public void set_protocol(Value protocol) {
            setProtocol(protocol);
        }

        public Value getProtocol() {
            return protocol;
        }

        public Value get_protocol() {
            return getProtocol();
        }
    }

    /** The connections a server accepted, so {@code wait_closed()} can wait for them. */
    private static final class AcceptedClients {
        private final Set<Channel> channels = ConcurrentHashMap.newKeySet();
        private volatile @Nullable Runnable onChange;

        void add(Channel channel) {
            channels.add(channel);
            channel.closeFuture().addListener(ignored -> {
                channels.remove(channel);
                Runnable listener = onChange;
                if (listener != null) {
                    listener.run();
                }
            });
        }
    }

    /**
     * Netty-backed asyncio server exposed to Python as a host object.
     *
     * <p>Like transports, this host object exposes snake_case members because
     * asyncio server helpers call names such as {@code start_serving},
     * {@code serve_forever}, and {@code wait_closed} directly.</p>
     */
    @SuppressWarnings({"EffectivelyPrivate", "UnusedMethod", "checkstyle:MethodName"})
    @Experimental
    public static final class NettyServer {
        private final List<Channel> channels;
        private final AcceptedClients clients;
        private final CompletableFuture<Void> closed;
        /** Listeners closed and every accepted connection gone: what wait_closed waits for. */
        private final CompletableFuture<Void> drained = new CompletableFuture<>();
        private volatile boolean serving;
        private volatile boolean closing;

        private NettyServer(List<Channel> channels, AcceptedClients clients, boolean startServing) {
            this.channels = channels;
            this.clients = clients;
            this.serving = startServing;
            CompletableFuture<?>[] closeFutures = channels.stream().map(channel -> {
                CompletableFuture<Void> closeFuture = new CompletableFuture<>();
                channel.closeFuture().addListener(future -> closeFuture.complete(null));
                return closeFuture;
            }).toArray(CompletableFuture[]::new);
            this.closed = CompletableFuture.allOf(closeFutures);
            this.closed.thenRun(this::checkDrained);
            clients.onChange = this::checkDrained;
            checkDrained();
            if (!startServing) {
                /*
                 * asyncio.start_server(..., start_serving=False) binds the server
                 * sockets without accepting connections until start_serving() runs.
                 */
                channels.forEach(channel -> channel.config().setAutoRead(false));
            }
        }

        private void checkDrained() {
            if (closed.isDone() && clients.channels.isEmpty()) {
                drained.complete(null);
            }
        }

        public Object[] sockets() {
            return channels.stream().map(NettyServerSocket::new).toArray();
        }

        public void startServing() {
            serving = true;
            for (Channel channel : channels) {
                channel.config().setAutoRead(true);
                channel.read();
            }
        }

        public void start_serving() {
            startServing();
        }

        public CompletionStage<Void> serveForever() {
            startServing();
            return waitClosed();
        }

        public CompletionStage<Void> serve_forever() {
            return serveForever();
        }

        public void close() {
            if (closing) {
                return;
            }
            closing = true;
            channels.forEach(channel -> channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));
        }

        public CompletionStage<Void> waitClosed() {
            // asyncio 3.12 semantics: the listeners are closed and the accepted connections are gone;
            // a copy, so a cancelled Python await cancels its own stage, not the server's
            return drained.copy();
        }

        public CompletionStage<Void> wait_closed() {
            return waitClosed();
        }

        public boolean isServing() {
            return !closing && serving && channels.stream().allMatch(Channel::isActive);
        }

        public boolean is_serving() {
            return isServing();
        }
    }

    /**
     * Socket-like object for Python's server.sockets API.
     *
     * <p>Only address accessors are exposed. The accepted channel itself remains
     * internal to avoid Python code mutating Netty state outside this event-loop
     * bridge.</p>
     */
    @SuppressWarnings("UnusedMethod")
    @Experimental
    public static final class NettyServerSocket {
        private final Channel channel;

        private NettyServerSocket(Channel channel) {
            this.channel = channel;
        }

        public @Nullable Object getsockname() {
            return toPythonAddress(channel.localAddress());
        }

        public @Nullable Object getpeername() {
            return toPythonAddress(channel.remoteAddress());
        }
    }

    /**
     * Socket-like facade for transport extras. Raw Netty channels stay internal to the runtime.
     *
     * <p>Python libraries commonly inspect {@code transport.get_extra_info("socket")}
     * for {@code getsockname()} or {@code getpeername()}. This facade satisfies
     * that expectation without exposing the Netty channel object.</p>
     */
    @SuppressWarnings("UnusedMethod")
    @Experimental
    public static final class NettySocketFacade {
        private final Channel channel;

        private NettySocketFacade(Channel channel) {
            this.channel = channel;
        }

        public @Nullable Object getsockname() {
            return toPythonAddress(channel.localAddress());
        }

        public @Nullable Object getpeername() {
            return toPythonAddress(channel.remoteAddress());
        }
    }

    /**
     * SSL facade for Python transport extras. It exposes stable session data without exposing Netty handlers.
     *
     * <p>The values intentionally follow the shape of Python ssl transport extras
     * closely enough for inspection and tests. The Netty {@link SslHandler} and
     * underlying {@code SSLEngine} stay hidden.</p>
     */
    @SuppressWarnings("UnusedMethod")
    @Experimental
    public static final class NettySslFacade {
        private final SslHandler sslHandler;

        private NettySslFacade(SslHandler sslHandler) {
            this.sslHandler = sslHandler;
        }

        public Object[] cipher() {
            return new Object[] {
                sslHandler.engine().getSession().getCipherSuite(),
                sslHandler.engine().getSession().getProtocol(),
                0
            };
        }

        public String version() {
            return sslHandler.engine().getSession().getProtocol();
        }

        public @Nullable Object getpeercert() {
            try {
                return sslHandler.engine().getSession().getPeerCertificates();
            } catch (javax.net.ssl.SSLPeerUnverifiedException e) {
                return null;
            }
        }
    }

    /**
     * Netty-backed datagram transport exposed to Python as a host object.
     *
     * <p>Snake_case methods mirror asyncio datagram transport names. UDP writes
     * may resolve tuple-style target addresses asynchronously on the same Netty
     * event loop before flushing the packet.</p>
     */
    @SuppressWarnings({"EffectivelyPrivate", "UnusedMethod", "checkstyle:MethodName"})
    @Experimental
    public static final class NettyDatagramTransport {
        private final NettyPythonEventLoop loop;
        private final Channel channel;
        private final Value protocol;
        private final @Nullable InetSocketAddress remoteAddress;
        private final EventLoop eventLoop;
        private final NettyPythonEventLoopSupport support;
        private volatile boolean closing;

        private NettyDatagramTransport(NettyPythonEventLoop loop,
                                       Channel channel,
                                       Value protocol,
                                       @Nullable InetSocketAddress remoteAddress,
                                       EventLoop eventLoop,
                                       NettyPythonEventLoopSupport support) {
            this.loop = loop;
            this.channel = channel;
            this.protocol = protocol;
            this.remoteAddress = remoteAddress;
            this.eventLoop = eventLoop;
            this.support = support;
        }

        public void sendto(byte[] data) {
            sendto(data, null);
        }

        public void sendto(byte[] bytes, @Nullable Object address) {
            if (isClosing()) {
                return;
            }
            InetSocketAddress targetAddress = address == null ? remoteAddress : toSocketAddress(address);
            if (targetAddress == null || !targetAddress.isUnresolved()) {
                writeDatagram(bytes, targetAddress);
            } else {
                support.resolver(eventLoop).getResolver(eventLoop).resolve(targetAddress).addListener(resolveFuture -> {
                    if (isClosing()) {
                        // closed while the name resolved: nothing is written or reported after connection_lost
                        return;
                    }
                    if (resolveFuture.isSuccess()) {
                        writeDatagram(bytes, (InetSocketAddress) resolveFuture.getNow());
                    } else {
                        guestRun(loop, protocol, () -> protocol.invokeMember("error_received", resolveFuture.cause()));
                    }
                });
            }
        }

        private void writeDatagram(byte[] bytes, @Nullable InetSocketAddress targetAddress) {
            if (isClosing()) {
                return;
            }
            // A connected channel writes plain buffers: the native transports reject an addressed
            // packet on a connected socket (sendto fails with EISCONN), NIO merely tolerates it.
            boolean connected = remoteAddress != null && (targetAddress == null || targetAddress.equals(remoteAddress));
            Object message = targetAddress == null || connected
                ? Unpooled.wrappedBuffer(bytes)
                : new DatagramPacket(Unpooled.wrappedBuffer(bytes), targetAddress);
            channel.writeAndFlush(message).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess() && !isClosing()) {
                    guestRun(loop, protocol, () -> protocol.invokeMember("error_received", future.cause()));
                }
            });
        }

        public @Nullable Object getExtraInfo(String name) {
            return getExtraInfo(name, null);
        }

        public @Nullable Object getExtraInfo(String name, @Nullable Object defaultValue) {
            return switch (name) {
                case "socket" -> new NettySocketFacade(channel);
                case "micronaut.netty" -> true;
                case "sockname" -> channel.localAddress() instanceof InetSocketAddress address ? toPythonAddress(address) : defaultValue;
                case "peername" -> remoteAddress == null ? defaultValue : toPythonAddress(remoteAddress);
                default -> defaultValue;
            };
        }

        public @Nullable Object get_extra_info(String name) {
            return getExtraInfo(name);
        }

        public @Nullable Object get_extra_info(String name, @Nullable Object defaultValue) {
            return getExtraInfo(name, defaultValue);
        }

        public boolean isClosing() {
            // closed by the transport, by the peer, or by the provider whose shutdown has begun
            return closing || !channel.isOpen() || support.isClosed();
        }

        public boolean is_closing() {
            return isClosing();
        }

        public void close() {
            if (closing) {
                return;
            }
            closing = true;
            // the datagram handler reports connection_lost when the channel goes inactive
            channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }

        public void abort() {
            close();
        }

        @SuppressWarnings("DoNotCallSuggester")
        public void setProtocol(Value protocol) {
            throw new UnsupportedOperationException("Changing the protocol of a Netty-backed Python transport is not supported");
        }

        public void set_protocol(Value protocol) {
            setProtocol(protocol);
        }

        public Value getProtocol() {
            return protocol;
        }

        public Value get_protocol() {
            return getProtocol();
        }
    }
}
