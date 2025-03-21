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
import io.netty.channel.Channel;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for KQueueEventLoopGroup.
 *
 * @author croudet
 */
@Singleton
@Internal
@Requires(classes = KQueue.class, condition = KQueueAvailabilityCondition.class)
@Named(EventLoopGroupFactory.NATIVE)
@BootstrapContextCompatible
public class KQueueEventLoopGroupFactory implements EventLoopGroupFactory {
    private static final Logger LOG = LoggerFactory.getLogger(KQueueEventLoopGroupFactory.class);

    @Override
    public IoHandlerFactory createIoHandlerFactory() {
        return KQueueIoHandler.newFactory();
    }

    @Override
    public boolean isNative() {
        return true;
    }

    @Override
    public Class<? extends Channel> channelClass(NettyChannelType type) throws UnsupportedOperationException {
        return switch (type) {
            case SERVER_SOCKET -> KQueueServerSocketChannel.class;
            case CLIENT_SOCKET -> KQueueSocketChannel.class;
            case DOMAIN_SOCKET -> KQueueDomainSocketChannel.class;
            case DOMAIN_SERVER_SOCKET -> KQueueServerDomainSocketChannel.class;
            case DATAGRAM_SOCKET -> KQueueDatagramChannel.class;
        };
    }

    @Override
    public Class<? extends Channel> channelClass(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return channelClass(type);
    }

    @Override
    public Channel channelInstance(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return switch (type) {
            case SERVER_SOCKET -> new KQueueServerSocketChannel();
            case CLIENT_SOCKET -> new KQueueSocketChannel();
            case DOMAIN_SOCKET -> new KQueueDomainSocketChannel();
            case DOMAIN_SERVER_SOCKET -> new KQueueServerDomainSocketChannel();
            case DATAGRAM_SOCKET -> new KQueueDatagramChannel();
        };
    }

    @Override
    public Channel channelInstance(NettyChannelType type, EventLoopGroupConfiguration configuration, Channel parent, int fd) {
        if (parent != null) {
            LOG.warn("kqueue does not support FD-based channels with a parent channel. This may cause issues with HTTP2.");
        }
        return switch (type) {
            case SERVER_SOCKET -> new KQueueServerSocketChannel(fd);
            case CLIENT_SOCKET -> new KQueueSocketChannel(fd);
            case DOMAIN_SOCKET -> new KQueueDomainSocketChannel(fd);
            case DOMAIN_SERVER_SOCKET -> new KQueueServerDomainSocketChannel(fd);
            case DATAGRAM_SOCKET -> new KQueueDatagramChannel(fd);
        };
    }
}
