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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.netty.channel.Channel;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringDatagramChannel;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Factory for IOUringEventLoopGroup.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Singleton
@Requires(classes = IoUring.class, condition = IoUringAvailabilityCondition.class)
@Internal
@Named(IoUringEventLoopGroupFactory.NAME)
@BootstrapContextCompatible
// avoid collision with epoll. we prefer epoll because it supports more features (domain socket).
@Order(200)
public class IoUringEventLoopGroupFactory implements EventLoopGroupFactory {
    public static final String NAME = "io_uring";

    @Override
    public IoHandlerFactory createIoHandlerFactory() {
        return IoUringIoHandler.newFactory();
    }

    @Override
    public boolean isNative() {
        return true;
    }

    @Override
    public Class<? extends Channel> channelClass(NettyChannelType type) throws UnsupportedOperationException {
        return switch (type) {
            case SERVER_SOCKET -> IoUringServerSocketChannel.class;
            case CLIENT_SOCKET -> IoUringSocketChannel.class;
            case DATAGRAM_SOCKET -> IoUringDatagramChannel.class;
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
            case SERVER_SOCKET -> new IoUringServerSocketChannel();
            case CLIENT_SOCKET -> new IoUringSocketChannel();
            case DATAGRAM_SOCKET -> new IoUringDatagramChannel();
            default -> throw new UnsupportedOperationException("Channel type not supported");
        };
    }
}
