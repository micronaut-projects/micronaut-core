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

import io.micronaut.core.annotation.NonNull;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDomainSocketChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.Optional;

/**
 * Connection-level metadata for logging e.g. addresses or ports.
 *
 * @since 4.6.0
 * @author Jonas Konrad
 */
public interface ConnectionMetadata {
    /**
     * The local address of this connection, if applicable.
     *
     * @return The local address
     */
    @NonNull
    Optional<SocketAddress> localAddress();

    /**
     * The remote address of this connection, if applicable.
     *
     * @return The remote address
     */
    @NonNull
    Optional<SocketAddress> remoteAddress();

    /**
     * Get the host address string of the given {@link SocketAddress} instance. This is usually the
     * numeric IP, but for unix domain socket it is the file path.
     *
     * @param a The address
     * @return The string representation, or {@link Optional#empty()} if this type of address is
     * not supported
     */
    static Optional<String> getHostAddress(@NonNull SocketAddress a) {
        if (a instanceof InetSocketAddress addr) {
            return Optional.of(addr.getAddress().getHostAddress());
        } else {
            return getHostName(a);
        }
    }

    /**
     * Get the host name of the given {@link SocketAddress} instance. This is usually the DNS name
     * or IP, but for unix domain socket it is the file path.
     *
     * @param a The address
     * @return The string representation, or {@link Optional#empty()} if this type of address is
     * not supported
     */
    static Optional<String> getHostName(@NonNull SocketAddress a) {
        if (a instanceof InetSocketAddress addr) {
            return Optional.of(addr.getAddress().getHostName());
        } else if (a instanceof UnixDomainSocketAddress addr) {
            return Optional.of(addr.getPath().toString())
                // remote address is empty string
                .filter(s -> !s.isEmpty());
        } else if (ConnectionMetadataImpl.DOMAIN_SOCKET_ADDRESS.isInstance(a)) {
            String path = ConnectionMetadataImpl.DomainSocketUtil.getPath(a);
            if (path.isEmpty() || path.equals("\0")) {
                return Optional.empty();
            }
            if (path.startsWith("\0")) {
                // *abstract* unix domain sockets start with a NUL byte
                path = "@" + path.substring(1);
            }
            return Optional.of(path);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Create a new {@link ConnectionMetadata} instance for the given netty channel.
     *
     * @param channel The channel
     * @return The metadata, potentially {@link #empty()}
     */
    @NonNull
    static ConnectionMetadata ofNettyChannel(@NonNull Channel channel) {
        if (channel instanceof SocketChannel sc) {
            return new ConnectionMetadataImpl.SocketChannelMetadata(sc);
        } else if (ConnectionMetadataImpl.QUIC_CHANNEL != null && ConnectionMetadataImpl.QUIC_CHANNEL.isInstance(channel)) {
            return new ConnectionMetadataImpl.QuicChannelMetadata(channel);
        } else if (channel instanceof DatagramChannel || channel instanceof NioDomainSocketChannel || (ConnectionMetadataImpl.DOMAIN_SOCKET_CHANNEL != null && ConnectionMetadataImpl.DOMAIN_SOCKET_CHANNEL.isInstance(channel))) {
            return new ConnectionMetadataImpl.GenericChannelMetadata(channel);
        } else if (channel.parent() != null) {
            // QUIC / HTTP/2 stream channels
            return ofNettyChannel(channel.parent());
        } else {
            return empty();
        }
    }

    /**
     * Placeholder metadata for unsupported channel types.
     *
     * @return An empty metadata instance
     */
    @NonNull
    static ConnectionMetadata empty() {
        return ConnectionMetadataImpl.Empty.INSTANCE;
    }
}
