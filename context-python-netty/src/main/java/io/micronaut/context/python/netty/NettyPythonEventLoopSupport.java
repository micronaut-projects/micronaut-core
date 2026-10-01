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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.netty.channel.NettyChannelType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.IoHandle;
import io.netty.channel.IoEventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Internal Netty transport, DNS, and TLS support used by the Python asyncio loop.
 */
@Internal
final class NettyPythonEventLoopSupport {
    /** asyncio's {@code SSL_HANDSHAKE_TIMEOUT}, in seconds. */
    static final double DEFAULT_SSL_HANDSHAKE_TIMEOUT = 60.0;
    /** asyncio's {@code SSL_SHUTDOWN_TIMEOUT}, in seconds. */
    static final double DEFAULT_SSL_SHUTDOWN_TIMEOUT = 30.0;

    private final Set<Channel> channels = ConcurrentHashMap.newKeySet();
    /** Guards the closed flag, channel tracking and resolver creation against shutdown. */
    private final Object lifecycle = new Object();
    private volatile boolean closed;
    /** Completes once every tracked channel, late ones included, has closed after shutdown. */
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
    /** Factory operations (connect, bind) begun and not yet completed: shutdown waits for their channels. */
    private int pendingOperations;
    private final Map<EventLoop, AddressResolverGroup<InetSocketAddress>> resolvers = new ConcurrentHashMap<>();
    private final Map<EventLoop, Transport> transports = new ConcurrentHashMap<>();

    /**
     * The transport an event loop runs on. Netty 4.2 event loops are all
     * {@link SingleThreadIoEventLoop} instances whose transport is the {@code IoHandler} they were
     * created with, so the loop is asked which channel handles it accepts; the deprecated
     * transport-specific loops of Netty 4.1 are recognised by their class name. Resolved once per
     * loop and forgotten when the loop terminates.
     *
     * @param eventLoop The event loop
     * @return The transport
     */
    Transport transport(EventLoop eventLoop) {
        return transports.computeIfAbsent(eventLoop, loop -> {
            Transport transport = detectTransport(loop);
            loop.terminationFuture().addListener(ignored -> transports.remove(loop));
            return transport;
        });
    }

    private static Transport detectTransport(EventLoop eventLoop) {
        if (eventLoop instanceof IoEventLoop ioEventLoop) {
            for (Transport transport : Transport.NATIVE) {
                Class<? extends IoHandle> handle = transport.ioHandleClass();
                if (handle != null && ioEventLoop.isCompatible(handle)) {
                    return transport;
                }
            }
            return Transport.NIO;
        }
        String eventLoopClassName = eventLoop.getClass().getName();
        for (Transport transport : Transport.NATIVE) {
            if (eventLoopClassName.contains(transport.packageFragment)) {
                return transport;
            }
        }
        return Transport.NIO;
    }

    Class<? extends Channel> channelClass(EventLoop eventLoop, NettyChannelType type) {
        Transport transport = transport(eventLoop);
        if (transport.isNative()) {
            return nativeChannelClass(transport.nativeChannelPrefix(), type);
        }
        return switch (type) {
            case SERVER_SOCKET -> NioServerSocketChannel.class;
            case CLIENT_SOCKET -> NioSocketChannel.class;
            case DOMAIN_SERVER_SOCKET -> NioServerDomainSocketChannel.class;
            case DOMAIN_SOCKET -> NioDomainSocketChannel.class;
            case DATAGRAM_SOCKET -> NioDatagramChannel.class;
        };
    }

    AddressResolverGroup<InetSocketAddress> resolver(EventLoop eventLoop) {
        synchronized (lifecycle) {
            if (closed) {
                throw new IllegalStateException("The Python networking support of this application has been shut down");
            }
            return resolvers.computeIfAbsent(eventLoop, this::newResolver);
        }
    }

    @Nullable
    ChannelOption<Boolean> reusePortOption(EventLoop eventLoop) {
        return transport(eventLoop).isNative() ? UnixChannelOption.SO_REUSEPORT : null;
    }

    private AddressResolverGroup<InetSocketAddress> newResolver(EventLoop eventLoop) {
        AddressResolverGroup<InetSocketAddress> resolver = new DnsAddressResolverGroup(
            () -> (DatagramChannel) newChannel(eventLoop, NettyChannelType.DATAGRAM_SOCKET),
            DnsServerAddressStreamProviders.platformDefault()
        );
        eventLoop.terminationFuture().addListener(ignored -> {
            AddressResolverGroup<InetSocketAddress> removed = resolvers.remove(eventLoop);
            if (removed != null) {
                removed.close();
            }
        });
        return resolver;
    }

    /**
     * A datagram channel of the requested socket family, so a wildcard endpoint of
     * {@code family=AF_INET} is an IPv4 socket rather than a dual-stack one reporting {@code ::}.
     *
     * @param eventLoop The event loop
     * @param family {@code inet}, {@code inet6} or empty for the transport's default
     * @return The channel
     */
    Channel newDatagramChannel(EventLoop eventLoop, String family) {
        return newChannel(eventLoop, NettyChannelType.DATAGRAM_SOCKET, family);
    }

    /**
     * Whether this JVM can open IPv6 sockets: {@code false} on a host without IPv6 and with
     * {@code java.net.preferIPv4Stack=true}. Decided by opening one, not by reading exception text.
     *
     * @return Whether IPv6 sockets can be created
     */
    static boolean ipv6Available() {
        return Ipv6Probe.AVAILABLE;
    }

    /**
     * A server socket channel of the requested family, for a wildcard listener of one family.
     *
     * @param eventLoop The event loop
     * @param family {@code inet}, {@code inet6} or empty for the transport's default
     * @return The channel
     */
    Channel newServerChannel(EventLoop eventLoop, String family) {
        return newChannel(eventLoop, NettyChannelType.SERVER_SOCKET, family);
    }

    private Channel newChannel(EventLoop eventLoop, NettyChannelType type, String family) {
        SocketProtocolFamily socketFamily = switch (family) {
            case "inet" -> SocketProtocolFamily.INET;
            case "inet6" -> SocketProtocolFamily.INET6;
            default -> null;
        };
        if (socketFamily == null) {
            return newChannel(eventLoop, type);
        }
        Class<? extends Channel> channelClass = channelClass(eventLoop, type);
        try {
            // the datagram and native channels take the family alone, the NIO server channel with its provider
            for (Constructor<?> constructor : channelClass.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 1 && parameters[0] == SocketProtocolFamily.class) {
                    return (Channel) constructor.newInstance(socketFamily);
                }
                if (parameters.length == 2 && parameters[0] == SelectorProvider.class && parameters[1] == SocketProtocolFamily.class) {
                    return (Channel) constructor.newInstance(SelectorProvider.provider(), socketFamily);
                }
            }
            // a transport without a family-specific constructor: its default socket
            return newChannel(eventLoop, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create a " + family + " " + type + " channel of type " + channelClass.getName(), e);
        }
    }

    Channel newChannel(EventLoop eventLoop, NettyChannelType type) {
        try {
            return channelClass(eventLoop, type).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create Netty channel for " + type, e);
        }
    }

    SocketAddress domainSocketAddress(EventLoop eventLoop, String path) {
        if (transport(eventLoop).isNative()) {
            return DomainSocketAddressHolder.create(path);
        }
        return UnixDomainSocketAddress.of(path);
    }

    void track(Channel channel) {
        boolean closeNow;
        synchronized (lifecycle) {
            channels.add(channel);
            closeNow = closed;
        }
        channel.closeFuture().addListener(ignored -> untrack(channel));
        if (closeNow) {
            // opened after shutdown started: closed at once, and the shutdown waits for it like the others
            channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    }

    private void untrack(Channel channel) {
        synchronized (lifecycle) {
            channels.remove(channel);
        }
        completeIfDrained();
    }

    private void completeIfDrained() {
        boolean drainedNow;
        synchronized (lifecycle) {
            drainedNow = closed && pendingOperations == 0 && channels.isEmpty();
        }
        if (drainedNow) {
            drained.complete(null);
        }
    }

    /**
     * Register a factory operation (a connect or a bind) about to start: shutdown refuses new ones
     * and waits for the running ones, whose channels may only be tracked once they exist.
     *
     * @return The callback to run when the operation's future completes
     * @throws IllegalStateException After shutdown
     */
    Runnable beginOperation() {
        synchronized (lifecycle) {
            if (closed) {
                throw new IllegalStateException("The Python networking support of this application has been shut down");
            }
            pendingOperations++;
        }
        return () -> {
            synchronized (lifecycle) {
                pendingOperations--;
            }
            completeIfDrained();
        };
    }

    /**
     * Whether {@link #closeAll()} ran: channels tracked from then on are closed at once.
     *
     * @return {@code true} after shutdown
     */
    boolean isClosed() {
        return closed;
    }

    /**
     * Close every tracked channel and the resolvers. The stage completes once no tracked channel is
     * open, including channels tracked after the shutdown began; a resolver cannot be created after it.
     *
     * @return A stage completing when every tracked channel has closed
     */
    CompletionStage<Void> closeAll() {
        List<Channel> snapshot;
        List<AddressResolverGroup<InetSocketAddress>> resolverSnapshot;
        synchronized (lifecycle) {
            closed = true;
            snapshot = List.copyOf(channels);
            resolverSnapshot = List.copyOf(resolvers.values());
            resolvers.clear();
        }
        resolverSnapshot.forEach(AddressResolverGroup::close);
        for (Channel channel : snapshot) {
            if (channel.isOpen()) {
                channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            } else {
                untrack(channel);
            }
        }
        // nothing tracked and nothing in flight: complete now; otherwise the last close or completion does
        completeIfDrained();
        return drained.copy();
    }

    @Nullable TlsOptions tlsOptions(@Nullable Object ssl,
                                   boolean server,
                                   @Nullable String serverHostname,
                                   int port,
                                   @Nullable Double handshakeTimeout,
                                   @Nullable Double shutdownTimeout) throws SSLException {
        if (ssl == null || isPythonNone(ssl)) {
            return null;
        }
        if (ssl instanceof Boolean enabled) {
            if (!enabled) {
                return null;
            }
            if (server) {
                throw new IllegalArgumentException("Server TLS requires an explicit ssl mapping with certfile and keyfile");
            }
            return new TlsOptions(SslContextBuilder.forClient().build(), serverHostname, port, handshakeTimeout, shutdownTimeout);
        }
        if (ssl instanceof Map<?, ?> map) {
            SslContextBuilder builder = sslContextBuilder(map, server);
            return new TlsOptions(builder.build(), serverHostname, port, handshakeTimeout, shutdownTimeout);
        }
        if (ssl instanceof Value value) {
            if (value.isBoolean()) {
                return tlsOptions(value.asBoolean(), server, serverHostname, port, handshakeTimeout, shutdownTimeout);
            }
            if (!value.hasHashEntries() && !value.hasMember("get")) {
                throw new NotImplementedException("Python ssl.SSLContext is not supported by the Micronaut Netty asyncio event loop. Use ssl=True or an ssl={...} mapping.");
            }
            SslContextBuilder builder = sslContextBuilder(value, server);
            return new TlsOptions(builder.build(), serverHostname, port, handshakeTimeout, shutdownTimeout);
        }
        throw new NotImplementedException("Python ssl.SSLContext is not supported by the Micronaut Netty asyncio event loop. Use ssl=True or an ssl={...} mapping.");
    }

    static long timeoutMillis(@Nullable Double seconds, double defaultSeconds) {
        double value = seconds == null ? defaultSeconds : seconds;
        return Math.max(1, (long) Math.ceil(value * 1000));
    }

    SslHandler sslHandler(Channel channel, TlsOptions options) {
        SslHandler handler = options.serverHostname == null || options.serverHostname.isBlank()
            ? options.sslContext.newHandler(channel.alloc())
            : options.sslContext.newHandler(channel.alloc(), options.serverHostname, options.port);
        // asyncio's defaults when none is given (60 s handshake, 30 s shutdown), and a positive
        // timeout below a millisecond rounds up rather than to Netty's "no timeout" zero
        handler.setHandshakeTimeout(timeoutMillis(options.handshakeTimeout, DEFAULT_SSL_HANDSHAKE_TIMEOUT), TimeUnit.MILLISECONDS);
        long shutdownMillis = timeoutMillis(options.shutdownTimeout, DEFAULT_SSL_SHUTDOWN_TIMEOUT);
        handler.setCloseNotifyFlushTimeoutMillis(shutdownMillis);
        handler.setCloseNotifyReadTimeoutMillis(shutdownMillis);
        return handler;
    }

    private SslContextBuilder sslContextBuilder(Value options, boolean server) {
        return sslContextBuilder(new TlsOptionAccessor() {
            @Override
            public @Nullable String stringOption(String name) {
                return NettyPythonEventLoopSupport.stringOption(options, name);
            }

            @Override
            public boolean booleanOption(String name, boolean defaultValue) {
                return NettyPythonEventLoopSupport.booleanOption(options, name, defaultValue);
            }

            @Override
            public List<String> stringListOption(String name) {
                return NettyPythonEventLoopSupport.stringListOption(options, name);
            }
        }, server);
    }

    private SslContextBuilder sslContextBuilder(Map<?, ?> options, boolean server) {
        return sslContextBuilder(new TlsOptionAccessor() {
            @Override
            public @Nullable String stringOption(String name) {
                return NettyPythonEventLoopSupport.stringOption(options, name);
            }

            @Override
            public boolean booleanOption(String name, boolean defaultValue) {
                return NettyPythonEventLoopSupport.booleanOption(options, name, defaultValue);
            }

            @Override
            public List<String> stringListOption(String name) {
                return NettyPythonEventLoopSupport.stringListOption(options, name);
            }
        }, server);
    }

    private SslContextBuilder sslContextBuilder(TlsOptionAccessor options, boolean server) {
        String certfile = options.stringOption("certfile");
        String keyfile = options.stringOption("keyfile");
        String keyPassword = options.stringOption("key_password");
        String cafile = options.stringOption("cafile");
        boolean trustAll = options.booleanOption("trust_all", false);
        SslContextBuilder builder;
        if (server) {
            if (StringUtils.isEmpty(certfile) || StringUtils.isEmpty(keyfile)) {
                throw new IllegalArgumentException("Server TLS requires ssl['certfile'] and ssl['keyfile']");
            }
            builder = keyPassword == null
                ? SslContextBuilder.forServer(new File(certfile), new File(keyfile))
                : SslContextBuilder.forServer(new File(certfile), new File(keyfile), keyPassword);
        } else {
            builder = SslContextBuilder.forClient();
            if (StringUtils.isNotEmpty(certfile) && StringUtils.isNotEmpty(keyfile)) {
                builder.keyManager(new File(certfile), new File(keyfile), keyPassword);
            }
        }
        if (StringUtils.isNotEmpty(cafile)) {
            builder.trustManager(new File(cafile));
        } else if (trustAll) {
            builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
        }
        String clientAuth = options.stringOption("client_auth");
        if (clientAuth != null) {
            builder.clientAuth(switch (clientAuth.toLowerCase(Locale.ROOT)) {
                case "need", "required", "require" -> ClientAuth.REQUIRE;
                case "want", "optional" -> ClientAuth.OPTIONAL;
                case "none", "false" -> ClientAuth.NONE;
                default -> throw new IllegalArgumentException("Unsupported TLS client_auth value: " + clientAuth);
            });
        }
        List<String> protocols = options.stringListOption("protocols");
        if (!protocols.isEmpty()) {
            builder.protocols(protocols);
        }
        List<String> ciphers = options.stringListOption("ciphers");
        if (!ciphers.isEmpty()) {
            builder.ciphers(ciphers);
        }
        return builder;
    }

    /**
     * The Netty transports the Python event loop can run on. The native ones name their channel
     * classes {@code <prefix>SocketChannel}, {@code <prefix>ServerSocketChannel},
     * {@code <prefix>DomainSocketChannel}, {@code <prefix>ServerDomainSocketChannel} and
     * {@code <prefix>DatagramChannel}; the classes are resolved by name because the native
     * transports are optional dependencies.
     */
    enum Transport {
        EPOLL("io.netty.channel.epoll.Epoll", "io.netty.channel.epoll.EpollIoHandle", ".epoll."),
        KQUEUE("io.netty.channel.kqueue.KQueue", "io.netty.channel.kqueue.KQueueIoHandle", ".kqueue."),
        IO_URING("io.netty.channel.uring.IoUring", "io.netty.channel.uring.IoUringIoHandle", ".uring."),
        NIO(null, "io.netty.channel.nio.NioIoHandle", ".nio.");

        static final List<Transport> NATIVE = List.of(EPOLL, KQUEUE, IO_URING);

        private final @Nullable String channelPrefix;
        private final String ioHandleClassName;
        private final String packageFragment;

        Transport(@Nullable String channelPrefix, String ioHandleClassName, String packageFragment) {
            this.channelPrefix = channelPrefix;
            this.ioHandleClassName = ioHandleClassName;
            this.packageFragment = packageFragment;
        }

        boolean isNative() {
            return channelPrefix != null;
        }

        private String nativeChannelPrefix() {
            if (channelPrefix == null) {
                throw new IllegalStateException("The " + name() + " transport has no native channel classes");
            }
            return channelPrefix;
        }

        private @Nullable Class<? extends IoHandle> ioHandleClass() {
            try {
                return Class.forName(ioHandleClassName).asSubclass(IoHandle.class);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }

    private static Class<? extends Channel> nativeChannelClass(String prefix, NettyChannelType type) {
        String className = switch (type) {
            case SERVER_SOCKET -> prefix + "ServerSocketChannel";
            case CLIENT_SOCKET -> prefix + "SocketChannel";
            case DOMAIN_SERVER_SOCKET -> prefix + "ServerDomainSocketChannel";
            case DOMAIN_SOCKET -> prefix + "DomainSocketChannel";
            case DATAGRAM_SOCKET -> prefix + "DatagramChannel";
        };
        try {
            return Class.forName(className).asSubclass(Channel.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Native Netty channel class is not available: " + className, e);
        }
    }

    private static boolean isPythonNone(Object value) {
        return value instanceof Value pythonValue && pythonValue.isNull();
    }

    private static @Nullable Value option(Value options, String name) {
        Value value;
        if (options.hasHashEntries()) {
            value = options.getHashValue(name);
        } else if (options.hasMember("get")) {
            value = options.invokeMember("get", name);
        } else {
            value = null;
        }
        return value == null || value.isNull() ? null : value;
    }

    private static @Nullable String stringOption(Value options, String name) {
        Value value = option(options, name);
        return value == null ? null : value.asString();
    }

    private static boolean booleanOption(Value options, String name, boolean defaultValue) {
        Value value = option(options, name);
        return value == null ? defaultValue : value.asBoolean();
    }

    private static List<String> stringListOption(Value options, String name) {
        Value value = option(options, name);
        if (value == null) {
            return List.of();
        }
        if (value.isString()) {
            return List.of(value.asString());
        }
        if (!value.hasArrayElements()) {
            throw new IllegalArgumentException("TLS option [" + name + "] must be a string sequence");
        }
        List<String> values = new ArrayList<>((int) value.getArraySize());
        for (long i = 0; i < value.getArraySize(); i++) {
            values.add(value.getArrayElement(i).asString());
        }
        return values;
    }

    private static @Nullable String stringOption(Map<?, ?> options, String name) {
        Object value = options.get(name);
        return value == null ? null : value.toString();
    }

    private static boolean booleanOption(Map<?, ?> options, String name, boolean defaultValue) {
        Object value = options.get(name);
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private static List<String> stringListOption(Map<?, ?> options, String name) {
        Object value = options.get(name);
        if (value == null) {
            return List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                values.add(item.toString());
            }
            return values;
        }
        return List.of(value.toString());
    }

    private interface TlsOptionAccessor {
        @Nullable
        String stringOption(String name);

        boolean booleanOption(String name, boolean defaultValue);

        List<String> stringListOption(String name);
    }

    private static final class DomainSocketAddressHolder {
        private static SocketAddress create(String path) {
            try {
                return new DomainSocketAddress(path);
            } catch (NoClassDefFoundError e) {
                throw new UnsupportedOperationException("Netty domain socket support not on classpath", e);
            }
        }
    }

    record TlsOptions(SslContext sslContext,
                      @Nullable String serverHostname,
                      int port,
                      @Nullable Double handshakeTimeout,
                      @Nullable Double shutdownTimeout) {
    }

    static final class NotImplementedException extends RuntimeException {
        NotImplementedException(String message) {
            super(message);
        }
    }

    private static final class Ipv6Probe {
        private static final boolean AVAILABLE = probe();

        private static boolean probe() {
            try (ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.INET6)) {
                return channel.isOpen();
            } catch (UnsupportedOperationException | IOException e) {
                return false;
            }
        }
    }
}
