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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.incubator.channel.uring.IOUring;
import io.netty.incubator.channel.uring.IOUringDatagramChannel;
import io.netty.incubator.channel.uring.IOUringEventLoopGroup;
import io.netty.incubator.channel.uring.IOUringServerSocketChannel;
import io.netty.incubator.channel.uring.IOUringSocketChannel;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * Factory for IOUringEventLoopGroup.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Singleton
@Requires(classes = IOUring.class, condition = IoUringAvailabilityCondition.class)
@Internal
@Named(EventLoopGroupFactory.NATIVE)
@BootstrapContextCompatible
// avoid collision with epoll. we prefer epoll because it supports more features (domain socket).
@Secondary
public class IoUringEventLoopGroupFactory implements EventLoopGroupFactory {

    /**
     * Creates an IOUringEventLoopGroup.
     *
     * @param threads       The number of threads to use.
     * @param threadFactory The thread factory.
     * @param ioRatio       The io ratio.
     * @return An IOUringEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, ThreadFactory threadFactory, @Nullable Integer ioRatio) {
        return new IOUringEventLoopGroup(threads, threadFactory);
    }

    /**
     * Creates an IOUringEventLoopGroup.
     *
     * @param threads  The number of threads to use.
     * @param executor An Executor.
     * @param ioRatio  The io ratio.
     * @return An IOUringEventLoopGroup.
     */
    @Override
    public EventLoopGroup createEventLoopGroup(int threads, Executor executor, @Nullable Integer ioRatio) {
        return new IOUringEventLoopGroup(threads, executor);
    }

    /**
     * Returns the server channel class.
     *
     * @return IOUringServerSocketChannel.
     */
    @Override
    public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return IOUringServerSocketChannel.class;
    }

    @NonNull
    @Override
    public IOUringServerSocketChannel serverSocketChannelInstance(@Nullable EventLoopGroupConfiguration configuration) {
        return new IOUringServerSocketChannel();
    }

    @NonNull
    @Override
    public Class<? extends SocketChannel> clientSocketChannelClass(@Nullable EventLoopGroupConfiguration configuration) {
        return IOUringSocketChannel.class;
    }

    @Override
    public SocketChannel clientSocketChannelInstance(EventLoopGroupConfiguration configuration) {
        return new IOUringSocketChannel();
    }

    @Override
    public boolean isNative() {
        return true;
    }

    @Override
    public Class<? extends Channel> channelClass(NettyChannelType type) throws UnsupportedOperationException {
        return switch (type) {
            case SERVER_SOCKET -> IOUringServerSocketChannel.class;
            case CLIENT_SOCKET -> IOUringSocketChannel.class;
            case DATAGRAM_SOCKET -> IOUringDatagramChannel.class;
            default -> throw new UnsupportedOperationException("Channel type not supported");
        };
    }

    @Override
    public Class<? extends Channel> channelClass(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return channelClass(type);
    }

    @Override
    public Channel channelInstance(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return switch (type) {
            case SERVER_SOCKET -> new IOUringServerSocketChannel();
            case CLIENT_SOCKET -> new IOUringSocketChannel();
            case DATAGRAM_SOCKET -> new IOUringDatagramChannel();
            default -> throw new UnsupportedOperationException("Channel type not supported");
        };
    }
}
