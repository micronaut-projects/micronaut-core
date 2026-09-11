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
import org.jspecify.annotations.Nullable;
import io.netty.channel.Channel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
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
    @Nullable
    static final Class<?> QUIC_CHANNEL;
    @Nullable
    static final Class<?> DOMAIN_SOCKET_ADDRESS;
    @Nullable
    static final Class<?> DOMAIN_SOCKET_CHANNEL;

    static {
        // These types come from optional netty modules. They must be looked up by name: a class
        // literal is resolved by the JVM and a missing class surfaces as NoClassDefFoundError, which
        // is an Error, so a catch (Exception) around it never runs and this initializer fails.
        QUIC_CHANNEL = optionalClass("io.netty.handler.codec.quic.QuicChannel");
        DOMAIN_SOCKET_ADDRESS = optionalClass("io.netty.channel.unix.DomainSocketAddress");
        DOMAIN_SOCKET_CHANNEL = optionalClass("io.netty.channel.unix.DomainSocketChannel");
    }

    private ConnectionMetadataImpl() {
    }

    /**
     * Look up a class from an optional dependency.
     *
     * @param name The binary class name
     * @return The class, or {@code null} if it, or anything it depends on, is not available
     */
    @Nullable
    static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name, false, ConnectionMetadataImpl.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
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
        public Optional<SocketAddress> localAddress() {
            return Optional.of(ch.localAddress());
        }

        @Override
        public Optional<SocketAddress> remoteAddress() {
            return Optional.of(ch.remoteAddress());
        }
    }

    @Internal
    record GenericChannelMetadata(Channel ch) implements ConnectionMetadata {
        @Override
        public Optional<SocketAddress> localAddress() {
            return Optional.of(ch.localAddress());
        }

        @Override
        public Optional<SocketAddress> remoteAddress() {
            return Optional.of(ch.remoteAddress());
        }
    }

    @Internal
    record QuicChannelMetadata(Channel ch) implements ConnectionMetadata {
        @Override
        public Optional<SocketAddress> localAddress() {
            return Optional.ofNullable(((QuicChannel) ch).localSocketAddress());
        }

        @Override
        public Optional<SocketAddress> remoteAddress() {
            return Optional.ofNullable(((QuicChannel) ch).remoteSocketAddress());
        }
    }

    @Internal
    public static final class Empty implements ConnectionMetadata {
        static final ConnectionMetadata INSTANCE = new Empty();

        @Override
        public Optional<SocketAddress> localAddress() {
            return Optional.empty();
        }

        @Override
        public Optional<SocketAddress> remoteAddress() {
            return Optional.empty();
        }
    }
}
