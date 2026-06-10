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

import io.micronaut.context.python.GraalPyRuntimeUtil;
import io.micronaut.context.python.PythonEventLoop;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.channel.NettyChannelType;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
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
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
public final class NettyPythonEventLoop implements PythonEventLoop {
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
    public double time() {
        return System.nanoTime() / 1_000_000_000.0d;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof NettyPythonEventLoop other && eventLoop == other.eventLoop;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(eventLoop);
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
        CompletableFuture<Object[]> future = new CompletableFuture<>();
        Runnable connect = () -> {
            try {
                Value protocol = protocolFactory.execute();
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
                            closeOnCancellation(future, channel);
                            if (tlsOptions != null) {
                                channel.pipeline().addLast(support.sslHandler(channel, tlsOptions));
                            }
                            channel.pipeline().addLast(new NettySocketHandler(protocol, future, tlsOptions != null));
                        }
                    });
                InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved(host, port);
                InetSocketAddress localAddress = toUnresolvedSocketAddress(localHost, localPort);
                resolveAddress(localAddress, future, resolvedLocal -> {
                    if (future.isCancelled()) {
                        return;
                    }
                    if (resolvedLocal == null) {
                        bootstrap.connect(remoteAddress).addListener(closeOnFailure(future));
                    } else {
                        bootstrap.connect(remoteAddress, resolvedLocal).addListener(closeOnFailure(future));
                    }
                });
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (eventLoop.inEventLoop()) {
            connect.run();
        } else {
            eventLoop.execute(connect);
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
        CompletableFuture<NettyServer> future = new CompletableFuture<>();
        Runnable bind = () -> {
            try {
                NettyPythonEventLoopSupport.TlsOptions tlsOptions = support.tlsOptions(ssl, true, null, port, sslHandshakeTimeout, sslShutdownTimeout);
                ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(eventLoop, eventLoop)
                    .channel(serverChannelClass(NettyChannelType.SERVER_SOCKET))
                    .option(ChannelOption.SO_BACKLOG, backlog)
                    .option(ChannelOption.SO_REUSEADDR, reuseAddress)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            support.track(channel);
                            Value protocol = protocolFactory.execute();
                            if (tlsOptions != null) {
                                channel.pipeline().addLast(support.sslHandler(channel, tlsOptions));
                            }
                            channel.pipeline().addLast(new NettySocketHandler(protocol, null, tlsOptions != null));
                        }
                    });
                if (reusePort) {
                    ChannelOption<Boolean> reusePortOption = support.reusePortOption(eventLoop);
                    if (reusePortOption == null) {
                        throw new UnsupportedOperationException("reuse_port requires a Netty native transport that supports SO_REUSEPORT");
                    }
                    bootstrap.option(reusePortOption, true);
                }
                InetSocketAddress bindAddress = host == null ? new InetSocketAddress(port) : InetSocketAddress.createUnresolved(host, port);
                resolveAddress(bindAddress, future, resolvedBind -> {
                    if (future.isCancelled()) {
                        return;
                    }
                    bootstrap.bind(resolvedBind).addListener((ChannelFutureListener) bindFuture -> {
                        if (bindFuture.isSuccess()) {
                            support.track(bindFuture.channel());
                            closeOnCancellation(future, bindFuture.channel());
                            future.complete(new NettyServer(bindFuture.channel(), startServing));
                        } else {
                            future.completeExceptionally(bindFuture.cause());
                        }
                    });
                });
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (eventLoop.inEventLoop()) {
            bind.run();
        } else {
            eventLoop.execute(bind);
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
        Runnable connect = () -> {
            try {
                Channel channel = toChannel(socket);
                if (channel == null) {
                    future.complete(null);
                    return;
                }
                support.track(channel);
                closeOnCancellation(future, channel);
                Value protocol = protocolFactory.execute();
                NettyPythonEventLoopSupport.TlsOptions tlsOptions = support.tlsOptions(ssl, true, null, -1, sslHandshakeTimeout, sslShutdownTimeout);
                if (tlsOptions != null) {
                    channel.pipeline().addLast(support.sslHandler(channel, tlsOptions));
                }
                NettySocketHandler handler = new NettySocketHandler(protocol, future, tlsOptions != null);
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
            eventLoop.execute(connect);
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
        Runnable connect = () -> {
            try {
                Value protocol = protocolFactory.execute();
                NettyPythonEventLoopSupport.TlsOptions tlsOptions = support.tlsOptions(ssl, false, serverHostname, -1, sslHandshakeTimeout, sslShutdownTimeout);
                Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoop)
                    .channel(support.channelClass(eventLoop, NettyChannelType.DOMAIN_SOCKET))
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            support.track(channel);
                            closeOnCancellation(future, channel);
                            if (tlsOptions != null) {
                                channel.pipeline().addLast(support.sslHandler(channel, tlsOptions));
                            }
                            channel.pipeline().addLast(new NettySocketHandler(protocol, future, tlsOptions != null));
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
            eventLoop.execute(connect);
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
        Runnable bind = () -> {
            try {
                NettyPythonEventLoopSupport.TlsOptions tlsOptions = support.tlsOptions(ssl, true, null, -1, sslHandshakeTimeout, sslShutdownTimeout);
                ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(eventLoop, eventLoop)
                    .channel(serverChannelClass(NettyChannelType.DOMAIN_SERVER_SOCKET))
                    .option(ChannelOption.SO_BACKLOG, backlog)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            support.track(channel);
                            Value protocol = protocolFactory.execute();
                            if (tlsOptions != null) {
                                channel.pipeline().addLast(support.sslHandler(channel, tlsOptions));
                            }
                            channel.pipeline().addLast(new NettySocketHandler(protocol, null, tlsOptions != null));
                        }
                    });
                bootstrap.bind(support.domainSocketAddress(eventLoop, path)).addListener((ChannelFutureListener) bindFuture -> {
                    if (bindFuture.isSuccess()) {
                        support.track(bindFuture.channel());
                        closeOnCancellation(future, bindFuture.channel());
                        future.complete(new NettyServer(bindFuture.channel(), startServing));
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
            eventLoop.execute(bind);
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
        CompletableFuture<Object[]> future = new CompletableFuture<>();
        Runnable bind = () -> {
            try {
                Value protocol = protocolFactory.execute();
                InetSocketAddress local = toUnresolvedSocketAddress(localHost, localPort);
                InetSocketAddress remote = toUnresolvedSocketAddress(remoteHost, remotePort);
                Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoop)
                    .resolver(resolver())
                    .channel(support.channelClass(eventLoop, NettyChannelType.DATAGRAM_SOCKET))
                    .option(ChannelOption.SO_BROADCAST, allowBroadcast)
                    .handler(new NettyDatagramHandler(protocol));
                if (reusePort) {
                    ChannelOption<Boolean> reusePortOption = support.reusePortOption(eventLoop);
                    if (reusePortOption == null) {
                        throw new UnsupportedOperationException("reuse_port requires a Netty native transport that supports SO_REUSEPORT");
                    }
                    bootstrap.option(reusePortOption, true);
                }
                InetSocketAddress bindAddress = local == null ? new InetSocketAddress(0) : local;
                resolveAddress(bindAddress, future, resolvedBind -> bootstrap.bind(resolvedBind).addListener((ChannelFutureListener) bindFuture -> {
                    if (bindFuture.isSuccess()) {
                        Channel channel = bindFuture.channel();
                        support.track(channel);
                        closeOnCancellation(future, channel);
                        resolveAddress(remote, future, resolvedRemote -> finishDatagramConnect(protocol, future, channel, resolvedRemote));
                    } else {
                        future.completeExceptionally(bindFuture.cause());
                    }
                }));
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
        if (eventLoop.inEventLoop()) {
            bind.run();
        } else {
            eventLoop.execute(bind);
        }
        return future;
    }

    private void finishDatagramConnect(Value protocol,
                                       CompletableFuture<Object[]> future,
                                       Channel channel,
                                       @Nullable InetSocketAddress remote) {
        if (future.isCancelled()) {
            channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            return;
        }
        NettyDatagramTransport transport = new NettyDatagramTransport(channel, protocol, remote, eventLoop, support);
        Runnable connected = () -> {
            try {
                protocol.invokeMember("connection_made", transport);
                future.complete(new Object[] {transport, protocol});
            } catch (Throwable e) {
                channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                future.completeExceptionally(e);
            }
        };
        if (remote == null) {
            connected.run();
        } else {
            channel.connect(remote).addListener((ChannelFutureListener) connectFuture -> {
                if (connectFuture.isSuccess()) {
                    connected.run();
                } else {
                    channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                    future.completeExceptionally(connectFuture.cause());
                }
            });
        }
    }

    private void resolveAddress(@Nullable InetSocketAddress address,
                                CompletableFuture<?> future,
                                Consumer<@Nullable InetSocketAddress> consumer) {
        if (address == null || !address.isUnresolved()) {
            consumer.accept(address);
            return;
        }
        support.resolver(eventLoop).getResolver(eventLoop).resolve(address).addListener(resolveFuture -> {
            if (future.isCancelled()) {
                return;
            }
            if (resolveFuture.isSuccess()) {
                consumer.accept((InetSocketAddress) resolveFuture.getNow());
            } else {
                future.completeExceptionally(resolveFuture.cause());
            }
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

    @SuppressWarnings("FutureReturnValueIgnored")
    private static void closeOnCancellation(CompletableFuture<?> future, Channel channel) {
        future.whenComplete((ignored, throwable) -> {
            if (future.isCancelled() && channel.isOpen()) {
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
        return () -> {
            try (NettyPythonEventLoopProvider.Scope ignored = NettyPythonEventLoopProvider.bind(eventLoop)) {
                runnable.run();
            }
        };
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
                return InetSocketAddress.createUnresolved(value.getArrayElement(0).asString(), value.getArrayElement(1).asInt());
            }
        }
        if (address instanceof Object[] values && values.length >= 2) {
            return InetSocketAddress.createUnresolved(values[0].toString(), ((Number) values[1]).intValue());
        }
        throw new IllegalArgumentException("Unsupported datagram address: " + address);
    }

    private static @Nullable InetSocketAddress toUnresolvedSocketAddress(@Nullable String host, int port) {
        if (host == null || port < 0) {
            return null;
        }
        return InetSocketAddress.createUnresolved(host, port);
    }

    private static Object[] toPythonAddress(InetSocketAddress address) {
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

    private static Value pythonBytes(Context context, byte[] bytes) {
        return context.eval(GraalPyRuntimeUtil.PYTHON, "bytes").execute(bytes);
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
            }
        }
        return null;
    }

    private static final class NettySocketHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final Value protocol;
        private final @Nullable CompletableFuture<Object[]> connectedFuture;
        private final boolean tls;
        private @Nullable NettySocketTransport transport;
        private boolean connectionLost;

        private NettySocketHandler(Value protocol, @Nullable CompletableFuture<Object[]> connectedFuture, boolean tls) {
            this.protocol = protocol;
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
            NettySocketTransport newTransport = new NettySocketTransport(channel, protocol);
            transport = newTransport;
            try {
                protocol.invokeMember("connection_made", newTransport);
                if (connectedFuture != null) {
                    connectedFuture.complete(new Object[] {newTransport, protocol});
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
            byte[] bytes = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), bytes);
            protocol.invokeMember("data_received", pythonBytes(protocol.getContext(), bytes));
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

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            fireConnectionLost(null);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            fireConnectionLost(cause);
            ctx.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }

        private void fireConnectionLost(@Nullable Throwable cause) {
            if (connectionLost) {
                return;
            }
            connectionLost = true;
            protocol.invokeMember("connection_lost", cause);
        }
    }

    private static final class NettyDatagramHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final Value protocol;

        private NettyDatagramHandler(Value protocol) {
            this.protocol = protocol;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            ByteBuf content = packet.content();
            byte[] bytes = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), bytes);
            protocol.invokeMember(
                "datagram_received",
                pythonBytes(protocol.getContext(), bytes),
                toPythonAddress(packet.sender())
            );
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            protocol.invokeMember("error_received", cause);
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
    public static final class NettySocketTransport {
        private final Channel channel;
        private final Value protocol;
        private volatile boolean closing;

        private NettySocketTransport(Channel channel, Value protocol) {
            this.channel = channel;
            this.protocol = protocol;
        }

        public void write(byte[] bytes) {
            if (closing) {
                return;
            }
            channel.writeAndFlush(Unpooled.wrappedBuffer(bytes)).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    protocol.invokeMember("connection_lost", future.cause());
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
            return (int) channel.bytesBeforeUnwritable();
        }

        public int get_write_buffer_size() {
            return getWriteBufferSize();
        }

        public Object[] getWriteBufferLimits() {
            return new Object[] {0, channel.config().getWriteBufferHighWaterMark()};
        }

        public Object[] get_write_buffer_limits() {
            return getWriteBufferLimits();
        }

        public void setWriteBufferLimits(@Nullable Integer high, @Nullable Integer low) {
            set_write_buffer_limits(high, low);
        }

        public void set_write_buffer_limits(@Nullable Integer high, @Nullable Integer low) {
            if (high != null) {
                channel.config().setWriteBufferHighWaterMark(high);
            }
            if (low != null) {
                channel.config().setWriteBufferLowWaterMark(low);
            }
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
            return closing;
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

        public Value getProtocol() {
            return protocol;
        }

        public Value get_protocol() {
            return getProtocol();
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
    public static final class NettyServer {
        private final Channel channel;
        private final CompletableFuture<Void> closed = new CompletableFuture<>();
        private volatile boolean serving;
        private volatile boolean closing;

        private NettyServer(Channel channel, boolean startServing) {
            this.channel = channel;
            this.serving = startServing;
            channel.closeFuture().addListener(future -> closed.complete(null));
            if (!startServing) {
                /*
                 * asyncio.start_server(..., start_serving=False) binds the server
                 * socket without accepting connections until start_serving() runs.
                 */
                channel.config().setAutoRead(false);
            }
        }

        public Object[] sockets() {
            return new Object[] {new NettyServerSocket(channel)};
        }

        public void startServing() {
            serving = true;
            channel.config().setAutoRead(true);
            channel.read();
        }

        public void start_serving() {
            startServing();
        }

        public CompletionStage<Void> serveForever() {
            startServing();
            return closed;
        }

        public CompletionStage<Void> serve_forever() {
            return serveForever();
        }

        public void close() {
            if (closing) {
                return;
            }
            closing = true;
            channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }

        public CompletionStage<Void> waitClosed() {
            return closed;
        }

        public CompletionStage<Void> wait_closed() {
            return waitClosed();
        }

        public boolean isServing() {
            return !closing && serving && channel.isActive();
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
    public static final class NettyDatagramTransport {
        private final Channel channel;
        private final Value protocol;
        private final @Nullable InetSocketAddress remoteAddress;
        private final EventLoop eventLoop;
        private final NettyPythonEventLoopSupport support;
        private volatile boolean closing;

        private NettyDatagramTransport(Channel channel,
                                       Value protocol,
                                       @Nullable InetSocketAddress remoteAddress,
                                       EventLoop eventLoop,
                                       NettyPythonEventLoopSupport support) {
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
            InetSocketAddress targetAddress = address == null ? remoteAddress : toSocketAddress(address);
            if (targetAddress == null || !targetAddress.isUnresolved()) {
                writeDatagram(bytes, targetAddress);
            } else {
                support.resolver(eventLoop).getResolver(eventLoop).resolve(targetAddress).addListener(resolveFuture -> {
                    if (resolveFuture.isSuccess()) {
                        writeDatagram(bytes, (InetSocketAddress) resolveFuture.getNow());
                    } else {
                        protocol.invokeMember("error_received", resolveFuture.cause());
                    }
                });
            }
        }

        private void writeDatagram(byte[] bytes, @Nullable InetSocketAddress targetAddress) {
            Object message = targetAddress == null
                ? Unpooled.wrappedBuffer(bytes)
                : new DatagramPacket(Unpooled.wrappedBuffer(bytes), targetAddress);
            channel.writeAndFlush(message).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    protocol.invokeMember("error_received", future.cause());
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
            return closing;
        }

        public boolean is_closing() {
            return isClosing();
        }

        public void close() {
            if (closing) {
                return;
            }
            closing = true;
            channel.close().addListener((ChannelFutureListener) future -> protocol.invokeMember("connection_lost", (Object) null));
        }

        public void abort() {
            close();
        }
    }
}
