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
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;

/**
 * A server channel over a socket that {@link RetainedServerSockets} owns: it accepts on the socket while
 * registered, and closing it deregisters it without closing the socket, which keeps listening for the
 * next server.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class RetainedNioServerSocketChannel extends NioServerSocketChannel {

    private volatile boolean closed;

    RetainedNioServerSocketChannel(ServerSocketChannel socket) {
        super(socket);
    }

    @Override
    public boolean isOpen() {
        return !closed && super.isOpen();
    }

    @Override
    protected void doBind(SocketAddress localAddress) {
        throw new UnsupportedOperationException("A retained socket is bound already");
    }

    @Override
    protected void doClose() {
        // the socket belongs to whoever retained it: the channel is deregistered, the socket keeps its port and backlog
        closed = true;
    }
}
