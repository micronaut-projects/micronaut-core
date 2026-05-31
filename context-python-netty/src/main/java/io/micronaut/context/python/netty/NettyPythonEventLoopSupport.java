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
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
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
    private static final String EPOLL = ".epoll.";
    private static final String KQUEUE = ".kqueue.";
    private final Set<Channel> channels = ConcurrentHashMap.newKeySet();

    Class<? extends Channel> channelClass(EventLoop eventLoop, NettyChannelType type) {
        String eventLoopClassName = eventLoop.getClass().getName();
        if (eventLoopClassName.contains(EPOLL)) {
            return nativeChannelClass("io.netty.channel.epoll.Epoll", type);
        }
        if (eventLoopClassName.contains(KQUEUE)) {
            return nativeChannelClass("io.netty.channel.kqueue.KQueue", type);
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
        return new DnsAddressResolverGroup(
            () -> (DatagramChannel) newChannel(eventLoop, NettyChannelType.DATAGRAM_SOCKET),
            DnsServerAddressStreamProviders.platformDefault()
        );
    }

    Channel newChannel(EventLoop eventLoop, NettyChannelType type) {
        try {
            return channelClass(eventLoop, type).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create Netty channel for " + type, e);
        }
    }

    SocketAddress domainSocketAddress(EventLoop eventLoop, String path) {
        String eventLoopClassName = eventLoop.getClass().getName();
        if (eventLoopClassName.contains(EPOLL) || eventLoopClassName.contains(KQUEUE)) {
            return DomainSocketAddressHolder.create(path);
        }
        return UnixDomainSocketAddress.of(path);
    }

    void track(Channel channel) {
        channels.add(channel);
        channel.closeFuture().addListener(ignored -> channels.remove(channel));
    }

    CompletionStage<Void> closeAll() {
        List<Channel> snapshot = List.copyOf(channels);
        if (snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?>[] futures = snapshot.stream()
            .map(channel -> {
                CompletableFuture<Void> closeFuture = new CompletableFuture<>();
                channel.closeFuture().addListener(ignored -> closeFuture.complete(null));
                if (channel.isOpen()) {
                    ChannelFuture channelFuture = channel.close();
                    channelFuture.addListener(ignored -> {
                    });
                } else {
                    closeFuture.complete(null);
                }
                return closeFuture;
            })
            .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
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

    SslHandler sslHandler(Channel channel, TlsOptions options) {
        SslHandler handler = options.serverHostname == null || options.serverHostname.isBlank()
            ? options.sslContext.newHandler(channel.alloc())
            : options.sslContext.newHandler(channel.alloc(), options.serverHostname, options.port);
        if (options.handshakeTimeout != null) {
            handler.setHandshakeTimeout((long) (options.handshakeTimeout * 1000), TimeUnit.MILLISECONDS);
        }
        if (options.shutdownTimeout != null) {
            long millis = (long) (options.shutdownTimeout * 1000);
            handler.setCloseNotifyFlushTimeoutMillis(millis);
            handler.setCloseNotifyReadTimeoutMillis(millis);
        }
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
}
