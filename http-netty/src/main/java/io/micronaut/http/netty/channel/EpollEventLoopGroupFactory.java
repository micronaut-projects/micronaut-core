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
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for EpollEventLoopGroup.
 *
 * @author croudet
 */
@Singleton
@Requires(classes = Epoll.class, condition = EpollAvailabilityCondition.class)
@Internal
@Named(EpollEventLoopGroupFactory.NAME)
@BootstrapContextCompatible
@Order(100)
public class EpollEventLoopGroupFactory implements EventLoopGroupFactory {
    public static final String NAME = "epoll";
    private static final Logger LOG = LoggerFactory.getLogger(EpollEventLoopGroupFactory.class);

    @Override
    public IoHandlerFactory createIoHandlerFactory() {
        return EpollIoHandler.newFactory();
    }

    @Override
    public boolean isNative() {
        return true;
    }

    @Override
    public Class<? extends Channel> channelClass(NettyChannelType type) throws UnsupportedOperationException {
        return switch (type) {
            case SERVER_SOCKET -> EpollServerSocketChannel.class;
            case CLIENT_SOCKET -> EpollSocketChannel.class;
            case DOMAIN_SOCKET -> EpollDomainSocketChannel.class;
            case DOMAIN_SERVER_SOCKET -> EpollServerDomainSocketChannel.class;
            case DATAGRAM_SOCKET -> EpollDatagramChannel.class;
        };
    }

    @Override
    public Class<? extends Channel> channelClass(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return channelClass(type);
    }

    @Override
    public Channel channelInstance(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return switch (type) {
            case SERVER_SOCKET -> new EpollServerSocketChannel();
            case CLIENT_SOCKET -> new EpollSocketChannel();
            case DOMAIN_SOCKET -> new EpollDomainSocketChannel();
            case DOMAIN_SERVER_SOCKET -> new EpollServerDomainSocketChannel();
            case DATAGRAM_SOCKET -> new EpollDatagramChannel();
        };
    }

    @Override
    public Channel channelInstance(NettyChannelType type, EventLoopGroupConfiguration configuration, Channel parent, int fd) {
        if (parent != null) {
            LOG.warn("epoll does not support FD-based channels with a parent channel. This may cause issues with HTTP2.");
        }
        return switch (type) {
            case SERVER_SOCKET -> new EpollServerSocketChannel(fd);
            case CLIENT_SOCKET -> new EpollSocketChannel(fd);
            case DOMAIN_SOCKET -> new EpollDomainSocketChannel(fd);
            case DOMAIN_SERVER_SOCKET -> new EpollServerDomainSocketChannel(fd, true);
            case DATAGRAM_SOCKET -> new EpollDatagramChannel(fd);
        };
    }
}
