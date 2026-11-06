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
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;

/**
 * Listening sockets that outlive the server that accepts on them. A TCP listener of a server on the NIO
 * transport asks for one before it binds: given one, the server accepts on that socket and, when it
 * stops, stops accepting without closing it. The connections that arrive while no server accepts wait
 * in the socket's backlog for the next server, rather than being refused.
 *
 * <p>Development mode provides it, so that the port stays bound while one generation of the
 * application stops and the next one starts.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public interface RetainedServerSockets {

    /**
     * A bound, listening socket for a listener, kept by the caller for the life of the process.
     *
     * @param host The host the listener binds, null for the wildcard address
     * @param port The port the listener binds, 0 for a random one
     * @return The socket, or null for the listener to bind its own as usual
     * @throws IOException When the socket cannot be bound
     */
    @Nullable
    ServerSocketChannel serverSocket(@Nullable String host, int port) throws IOException;

    /**
     * Called once a server accepts on a socket {@link #serverSocket(String, int)} returned.
     *
     * @param socket The socket
     * @param channel The server's channel over it, closed when that server stops accepting on it
     */
    default void accepting(ServerSocketChannel socket, Channel channel) {
    }
}
