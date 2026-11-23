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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;

/**
 * The addresses of a QUIC connection, from when it was accepted, for the requests on its streams.
 * A client that migrates to another address keeps the one it connected from, and it is still there
 * when the connection is closed.
 *
 * <p>This class does not refer to the QUIC classes, which are optional.</p>
 *
 * @param remote The address of the client
 * @since 5.3.0
 */
@Internal
record QuicConnectionAddresses(InetSocketAddress remote) {
    private static final AttributeKey<QuicConnectionAddresses> KEY = AttributeKey.valueOf(QuicConnectionAddresses.class, "addresses");

    /**
     * Set the addresses of a QUIC connection.
     *
     * @param connection The connection
     */
    void set(Channel connection) {
        connection.attr(KEY).set(this);
    }

    /**
     * Find the addresses of the QUIC connection of a channel.
     *
     * @param stream The channel of a request
     * @return The addresses, or {@code null} if the channel is not of a QUIC stream
     */
    static @Nullable QuicConnectionAddresses find(Channel stream) {
        Channel connection = stream.parent();
        return connection != null && connection.hasAttr(KEY) ? connection.attr(KEY).get() : null;
    }
}
