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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.netty.channel.Channel;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Factory for NioEventLoopGroup.
 *
 * @author croudet
 */
@Internal
@Singleton
@Named(NioEventLoopGroupFactory.NAME)
@BootstrapContextCompatible
public class NioEventLoopGroupFactory implements EventLoopGroupFactory {
    public static final String NAME = "nio";

    @Override
    public IoHandlerFactory createIoHandlerFactory() {
        return NioIoHandler.newFactory();
    }

    @Override
    public Class<? extends Channel> channelClass(NettyChannelType type) throws UnsupportedOperationException {
        return switch (type) {
            case SERVER_SOCKET -> NioServerSocketChannel.class;
            case CLIENT_SOCKET -> NioSocketChannel.class;
            case DOMAIN_SERVER_SOCKET -> NioServerDomainSocketChannel.class;
            case DOMAIN_SOCKET -> NioDomainSocketChannel.class;
            case DATAGRAM_SOCKET -> NioDatagramChannel.class;
        };
    }

    @Override
    public Class<? extends Channel> channelClass(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return channelClass(type);
    }

    @Override
    public Channel channelInstance(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration) {
        return switch (type) {
            case SERVER_SOCKET -> new NioServerSocketChannel();
            case CLIENT_SOCKET -> new NioSocketChannel();
            case DOMAIN_SERVER_SOCKET -> new NioServerDomainSocketChannel();
            case DOMAIN_SOCKET -> new NioDomainSocketChannel();
            case DATAGRAM_SOCKET -> new NioDatagramChannel();
        };
    }

    @Override
    public @NonNull Channel channelInstance(NettyChannelType type, @Nullable EventLoopGroupConfiguration configuration, Channel parent, int fd) {
        if (fd != 0) {
            throw new IllegalArgumentException("With nio, only channel fd 0 is supported. Either switch to fd 0, or use the epoll transport that supports any fd.");
        }
        java.nio.channels.Channel inheritedChannel;
        try {
            inheritedChannel = System.inheritedChannel();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return switch (type) {
            case SERVER_SOCKET -> {
                if (!(inheritedChannel instanceof java.nio.channels.ServerSocketChannel ssc)) {
                    throw new IllegalArgumentException("Inherited channel is not a ServerSocketChannel. You probably have to pass it as 'accepted-fd' instead of 'fd'.");
                }
                yield new NioServerSocketChannel(ssc);
            }
            case CLIENT_SOCKET -> {
                if (!(inheritedChannel instanceof java.nio.channels.SocketChannel sc)) {
                    throw new IllegalArgumentException("Inherited channel is not a SocketChannel. You probably have to pass it as 'fd' instead of 'accepted-fd'.");
                }
                yield new NioSocketChannel(parent, sc);
            }
            case DOMAIN_SERVER_SOCKET -> {
                if (!(inheritedChannel instanceof java.nio.channels.ServerSocketChannel ssc)) {
                    throw new IllegalArgumentException("Inherited channel is not a ServerSocketChannel. You probably have to pass it as 'accepted-fd' instead of 'fd'.");
                }
                yield new NioServerDomainSocketChannel(ssc);
            }
            case DOMAIN_SOCKET -> {
                if (!(inheritedChannel instanceof java.nio.channels.SocketChannel sc)) {
                    throw new IllegalArgumentException("Inherited channel is not a SocketChannel. You probably have to pass it as 'fd' instead of 'accepted-fd'.");
                }
                yield new NioDomainSocketChannel(parent, sc);
            }
            case DATAGRAM_SOCKET -> {
                if (!(inheritedChannel instanceof java.nio.channels.DatagramChannel dc)) {
                    throw new IllegalArgumentException("Inherited channel is not a DatagramChannel.");
                }
                yield new NioDatagramChannel(dc);
            }
        };
    }
}
