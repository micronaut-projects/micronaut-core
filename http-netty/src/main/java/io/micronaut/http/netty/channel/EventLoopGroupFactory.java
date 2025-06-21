/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.netty.channel;

import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.ServerDomainSocketChannel;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * Factory for EventLoopGroup.
 *
 * @author croudet
 * @author graemerocher
 * @since 1.2.0
 */
@NextMajorVersion("This doesn't create event loops anymore. Rename to e.g. NettyTransport")
public interface EventLoopGroupFactory {
    /**
     * Qualifier used to resolve the native factory.
     */
    String NATIVE = "native";

    /**
     * @return Is this a native factory.
     */
    default boolean isNative() {
        return false;
    }

    /**
     * Create a new {@link IoHandlerFactory} for this transport.
     *
     * @return The handler factory
     * @since 4.9
     */
    @NonNull
    default IoHandlerFactory createIoHandlerFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Create a new {@link IoHandlerFactory} for this transport.
     *
     * @param configuration The netty configuration
     * @return The handler factory
     * @since 4.9
     */
    @NonNull
    default IoHandlerFactory createIoHandlerFactory(@NonNull EventLoopGroupConfiguration configuration) {
        return createIoHandlerFactory();
    }

    /**
     * Creates an EventLoopGroup.
     *
     * @param threads  The number of threads to use.
     * @param executor An Executor.
     * @param ioRatio  The io ratio.
     * @return An EventLoopGroup.
     * @deprecated Go through {@link #createIoHandlerFactory(EventLoopGroupConfiguration)}
     */
    @Deprecated
    default EventLoopGroup createEventLoopGroup(
            int threads,
            Executor executor,
            @Nullable Integer ioRatio
    ) {
        return new MultiThreadIoEventLoopGroup(threads, executor, createIoHandlerFactory());
    }

    /**
     * Create an event loop group for the given configuration and thread factory.
     * @param configuration The configuration
     * @param threadFactory The thread factory
     * @return The event loop group
     * @deprecated Go through {@link #createIoHandlerFactory(EventLoopGroupConfiguration)}
     */
    @Deprecated
    default EventLoopGroup createEventLoopGroup(
            EventLoopGroupConfiguration configuration, ThreadFactory threadFactory) {
        ArgumentUtils.requireNonNull("configuration", configuration);
        ArgumentUtils.requireNonNull("threadFactory", threadFactory);
        return createEventLoopGroup(
                DefaultEventLoopGroupRegistry.numThreads(configuration),
                threadFactory,
                configuration.getIoRatio().orElse(null)
        );
    }

    /**
     * Creates an EventLoopGroup.
     *
     * @param threads       The number of threads to use.
     * @param threadFactory The thread factory.
     * @param ioRatio       The io ratio.
     * @return An EventLoopGroup.
     * @deprecated Go through {@link #createIoHandlerFactory(EventLoopGroupConfiguration)}
     */
    @Deprecated
    default EventLoopGroup createEventLoopGroup(
            int threads,
            @Nullable ThreadFactory threadFactory,
            @Nullable Integer ioRatio
    ) {
        return new MultiThreadIoEventLoopGroup(threads, threadFactory, createIoHandlerFactory());
    }

    /**
     * Creates an EventLoopGroup.
     *
     * @param threads The number of threads to use.
     * @param ioRatio The io ratio.
     * @return An EventLoopGroup.
     * @deprecated Go through {@link #createIoHandlerFactory(EventLoopGroupConfiguration)}
     */
    @Deprecated
    default EventLoopGroup createEventLoopGroup(int threads, @Nullable Integer ioRatio) {
        return createEventLoopGroup(threads, (ThreadFactory) null, ioRatio);
    }

    /**
     * Returns the server channel class.
     *
     * @return A ServerChannelClass.
     * @deprecated Use {@link #channelClass(NettyChannelType)} instead
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    @NonNull
    default Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return channelClass(NettyChannelType.SERVER_SOCKET).asSubclass(ServerSocketChannel.class);
    }

    /**
     * Returns the domain socket server channel class.
     *
     * @return A ServerDomainSocketChannel class.
     * @throws UnsupportedOperationException if domain sockets are not supported.
     * @deprecated Use {@link #channelClass(NettyChannelType)} instead
     */
    @NonNull
    @Deprecated(since = "4.5.0", forRemoval = true)
    default Class<? extends ServerDomainSocketChannel> domainServerSocketChannelClass() throws UnsupportedOperationException {
        return channelClass(NettyChannelType.DOMAIN_SERVER_SOCKET).asSubclass(ServerDomainSocketChannel.class);
    }

    /**
     * Returns the domain socket server channel class.
     *
     * @param type Type of the channel to return
     * @return A channel class.
     * @throws UnsupportedOperationException if domain sockets are not supported.
     */
    @NonNull
    default Class<? extends Channel> channelClass(NettyChannelType type) throws UnsupportedOperationException {
        return switch (type) {
            case SERVER_SOCKET -> serverSocketChannelClass();
            case DOMAIN_SERVER_SOCKET -> domainServerSocketChannelClass();
            default -> throw new UnsupportedOperationException("Channel type not supported");
        };
    }

    /**
     * Returns the server channel class.
     *
     * @param configuration The configuration
     * @return A ServerSocketChannel class.
     * @deprecated Use {@link #channelClass(NettyChannelType, EventLoopGroupConfiguration)} instead
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    default @NonNull Class<? extends ServerSocketChannel> serverSocketChannelClass(@Nullable EventLoopGroupConfiguration configuration) {
        return channelClass(NettyChannelType.SERVER_SOCKET, configuration).asSubclass(ServerSocketChannel.class);
    }

    /**
     * Returns the domain socket server channel class.
     *
     * @param configuration The configuration
     * @return A ServerDomainSocketChannel implementation.
     * @throws UnsupportedOperationException if domain sockets are not supported.
     * @deprecated Use {@link #channelClass(NettyChannelType, EventLoopGroupConfiguration)} instead
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    default @NonNull Class<? extends ServerDomainSocketChannel> domainServerSocketChannelClass(@Nullable EventLoopGroupConfiguration configuration) {
        return channelClass(NettyChannelType.DOMAIN_SERVER_SOCKET, configuration).asSubclass(ServerDomainSocketChannel.class);
    }

    /**
     * Returns the domain socket server channel class.
     *
     * @param type Type of the channel to return
     * @param configuration The configuration
     * @return A channel implementation.
     * @throws UnsupportedOperationException if domain sockets are not supported.
     */
    default @NonNull Class<? extends Channel> channelClass(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return switch (type) {
            case SERVER_SOCKET -> serverSocketChannelClass(configuration);
            case CLIENT_SOCKET -> clientSocketChannelClass(configuration);
            case DOMAIN_SERVER_SOCKET -> domainServerSocketChannelClass(configuration);
            default -> throw new UnsupportedOperationException("Channel type not supported");
        };
    }

    /**
     * Returns the server channel class instance.
     *
     * @param configuration The configuration
     * @return A ServerSocketChannel instance.
     * @deprecated Use {@link #channelInstance(NettyChannelType, EventLoopGroupConfiguration)} instead
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    default @NonNull ServerSocketChannel serverSocketChannelInstance(@Nullable EventLoopGroupConfiguration configuration) {
        return (ServerSocketChannel) channelInstance(NettyChannelType.SERVER_SOCKET, configuration);
    }

    /**
     * Returns the domain socket server channel class.
     *
     * @param configuration The configuration
     * @return A ServerDomainSocketChannel implementation.
     * @throws UnsupportedOperationException if domain sockets are not supported.
     * @deprecated Use {@link #channelInstance(NettyChannelType, EventLoopGroupConfiguration)} instead
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    default @NonNull ServerChannel domainServerSocketChannelInstance(@Nullable EventLoopGroupConfiguration configuration) {
        return (ServerChannel) channelInstance(NettyChannelType.DOMAIN_SERVER_SOCKET, configuration);
    }

    /**
     * Returns the domain socket server channel class.
     *
     * @param type Type of the channel to return
     * @param configuration The configuration
     * @return A ServerDomainSocketChannel implementation.
     * @throws UnsupportedOperationException if domain sockets are not supported.
     */
    default @NonNull Channel channelInstance(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return switch (type) {
            case SERVER_SOCKET -> serverSocketChannelInstance(configuration);
            case CLIENT_SOCKET -> clientSocketChannelInstance(configuration);
            case DOMAIN_SERVER_SOCKET -> domainServerSocketChannelInstance(configuration);
            default -> throw new UnsupportedOperationException("Channel type not supported");
        };
    }

    /**
     * Returns the channel instance.
     *
     * @param type Type of the channel to return
     * @param configuration The configuration
     * @param fd The pre-defined file descriptor
     * @return A channel implementation.
     * @throws UnsupportedOperationException if domain sockets are not supported.
     * @deprecated Use {@link #channelInstance(NettyChannelType, EventLoopGroupConfiguration, Channel, int)} instead
     */
    @Deprecated(since = "4.4.0")
    default @NonNull Channel channelInstance(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration, int fd) {
        return channelInstance(type, configuration, null, fd);
    }

    /**
     * Returns the channel instance.
     *
     * @param type Type of the channel to return
     * @param configuration The configuration
     * @param parent The {@link Channel#parent() parent channel}
     * @param fd The pre-defined file descriptor
     * @return A channel implementation.
     * @throws UnsupportedOperationException if domain sockets are not supported.
     */
    default @NonNull Channel channelInstance(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration, @Nullable Channel parent, int fd) {
        throw new UnsupportedOperationException("This transport does not support creating channels from file descriptors. Please use kqueue or epoll.");
    }

    /**
     * Returns the client channel class.
     *
     * @param configuration The configuration
     * @return A SocketChannel class.
     * @deprecated Use {@link #channelClass(NettyChannelType, EventLoopGroupConfiguration)} instead
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    @NonNull
    default Class<? extends SocketChannel> clientSocketChannelClass(@Nullable EventLoopGroupConfiguration configuration) {
        return channelClass(NettyChannelType.CLIENT_SOCKET, configuration).asSubclass(SocketChannel.class);
    }

    /**
     * Returns the client channel class instance.
     *
     * @param configuration The configuration
     * @return A SocketChannel instance.
     * @deprecated Use {@link #channelInstance(NettyChannelType, EventLoopGroupConfiguration)} instead
     */
    @Deprecated(since = "4.5.0", forRemoval = true)
    default @NonNull SocketChannel clientSocketChannelInstance(@Nullable EventLoopGroupConfiguration configuration) {
        return (SocketChannel) channelInstance(NettyChannelType.CLIENT_SOCKET, configuration);
    }

}
