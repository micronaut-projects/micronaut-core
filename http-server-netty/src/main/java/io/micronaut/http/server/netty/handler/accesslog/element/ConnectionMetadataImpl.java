/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.netty.handler.accesslog.element;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.netty.channel.Channel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.handler.codec.quic.QuicChannel;

import java.net.SocketAddress;
import java.util.Optional;

/**
 * Implementations of {@link ConnectionMetadata}.
 *
 * @since 4.6.0
 * @author Jonas Konrad
 */
@Internal
final class ConnectionMetadataImpl {
    static final Class<?> QUIC_CHANNEL;
    static final Class<?> DOMAIN_SOCKET_ADDRESS;
    static final Class<?> DOMAIN_SOCKET_CHANNEL;

    static {
        Class<QuicChannel> quicChannelClass;
        try {
            quicChannelClass = QuicChannel.class;
        } catch (Exception e) {
            quicChannelClass = null;
        }
        QUIC_CHANNEL = quicChannelClass;

        Class<DomainSocketAddress> domainSocketAddressClass;
        Class<DomainSocketChannel> domainSocketChannelClass;
        try {
            domainSocketAddressClass = DomainSocketAddress.class;
            domainSocketChannelClass = DomainSocketChannel.class;
        } catch (Exception e) {
            domainSocketAddressClass = null;
            domainSocketChannelClass = null;
        }
        DOMAIN_SOCKET_ADDRESS = domainSocketAddressClass;
        DOMAIN_SOCKET_CHANNEL = domainSocketChannelClass;
    }

    static class DomainSocketUtil {
        static String getPath(SocketAddress address) {
            return ((DomainSocketAddress) address).path();
        }
    }

    /**
     * This one is separate from {@link GenericChannelMetadata} because it has special handling for
     * compatibility.
     *
     * @param ch The channel
     */
    @Internal
    record SocketChannelMetadata(SocketChannel ch) implements ConnectionMetadata {
        @Override
        public @NonNull Optional<SocketAddress> localAddress() {
            return Optional.of(ch.localAddress());
        }

        @Override
        public @NonNull Optional<SocketAddress> remoteAddress() {
            return Optional.of(ch.remoteAddress());
        }
    }

    @Internal
    record GenericChannelMetadata(Channel ch) implements ConnectionMetadata {
        @Override
        public @NonNull Optional<SocketAddress> localAddress() {
            return Optional.of(ch.localAddress());
        }

        @Override
        public @NonNull Optional<SocketAddress> remoteAddress() {
            return Optional.of(ch.remoteAddress());
        }
    }

    @Internal
    record QuicChannelMetadata(Channel ch) implements ConnectionMetadata {
        @Override
        public @NonNull Optional<SocketAddress> localAddress() {
            return Optional.ofNullable(((QuicChannel) ch).localSocketAddress());
        }

        @Override
        public @NonNull Optional<SocketAddress> remoteAddress() {
            return Optional.ofNullable(((QuicChannel) ch).remoteSocketAddress());
        }
    }

    @Internal
    public static final class Empty implements ConnectionMetadata {
        static final ConnectionMetadata INSTANCE = new Empty();

        @Override
        public @NonNull Optional<SocketAddress> localAddress() {
            return Optional.empty();
        }

        @Override
        public @NonNull Optional<SocketAddress> remoteAddress() {
            return Optional.empty();
        }
    }
}
