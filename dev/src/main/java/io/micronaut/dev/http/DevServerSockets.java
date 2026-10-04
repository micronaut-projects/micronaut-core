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
package io.micronaut.dev.http;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The listening sockets of the application's HTTP servers, bound once and kept for the life of the
 * development runtime, so that the port stays bound across generations: a connection that arrives while
 * one generation stops and the next starts waits in the socket's backlog, and the next generation's
 * server accepts it, instead of being refused.
 *
 * <p>While a batch is processed, the servers stop accepting, so that a new connection is served by
 * whichever generation runs once the batch is done. While no generation runs because the last one failed
 * to start, the sockets answer every connection with a 503 and a {@code Retry-After}, rather than
 * leaving it waiting.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class DevServerSockets implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DevServerSockets.class);
    private static final int BACKLOG = 1024;
    private static final int MAX_REQUEST_HEAD = 64 * 1024;
    private static final int READ_TIMEOUT_MILLIS = 2_000;
    /**
     * The first byte of a TLS record carrying a handshake, a ClientHello.
     */
    private static final int TLS_HANDSHAKE = 0x16;

    private final Map<String, ServerSocketChannel> sockets = new LinkedHashMap<>();
    private final List<Accepting> accepting = new ArrayList<>();
    private boolean closed;
    private @Nullable Responder responder;

    /**
     * The socket for a listener on a fixed port: bound the first time it is asked for, the same one afterwards. None
     * for a random port, which several listeners may ask for at once and which no generation has to keep, nor for a
     * port a server still accepts on, which the listener binds as usual, and fails to.
     *
     * @param host The host, null for the wildcard address
     * @param port The port
     * @return The socket, or null for the listener to bind its own
     * @throws IOException When it cannot be bound
     */
    public synchronized @Nullable ServerSocketChannel serverSocket(@Nullable String host, int port) throws IOException {
        if (closed) {
            throw new IOException("The development runtime is closed");
        }
        if (port <= 0) {
            return null;
        }
        // a server is about to accept on the sockets: the responder of a failed start lets go of them first
        stopResponder();
        InetSocketAddress address = host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port);
        // the resolved address: two spellings of one host share a socket
        String key = address.toString();
        ServerSocketChannel socket = sockets.get(key);
        if (socket != null && isAccepting(socket)) {
            return null;
        }
        if (socket == null || !socket.isOpen()) {
            socket = ServerSocketChannel.open();
            try {
                socket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                socket.bind(address, BACKLOG);
                socket.configureBlocking(false);
            } catch (IOException | RuntimeException e) {
                socket.close();
                throw e;
            }
            sockets.put(key, socket);
        }
        return socket;
    }

    /**
     * Records the server channel accepting on a socket, so that accepting can be paused while a batch is processed.
     *
     * @param socket The socket
     * @param autoRead Turns the channel's accepting on or off
     * @param open Whether the channel is still open
     */
    public synchronized void accepting(ServerSocketChannel socket, Consumer<Boolean> autoRead, BooleanSupplier open) {
        // a server that starts while a batch is processed is the batch's new generation: it accepts at once
        accepting.removeIf(entry -> !entry.open.getAsBoolean());
        accepting.add(new Accepting(socket, autoRead, open));
    }

    private boolean isAccepting(ServerSocketChannel socket) {
        for (Accepting entry : accepting) {
            if (entry.socket == socket && entry.open.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stops the servers accepting: new connections wait in the backlog.
     */
    public synchronized void pause() {
        if (responder == null) {
            // a socket the running generation does not accept on belongs to a listener its configuration no longer has
            releaseUnclaimed();
        }
        for (Accepting entry : accepting) {
            if (entry.open.getAsBoolean()) {
                entry.autoRead.accept(false);
            }
        }
    }

    private void releaseUnclaimed() {
        accepting.removeIf(entry -> !entry.open.getAsBoolean());
        sockets.values().removeIf(socket -> {
            if (isAccepting(socket)) {
                return false;
            }
            try {
                socket.close();
            } catch (IOException e) {
                LOG.debug("Cannot close {}", socket, e);
            }
            return true;
        });
    }

    /**
     * Lets the servers accept again, and the backlog through.
     */
    public synchronized void resume() {
        accepting.removeIf(entry -> !entry.open.getAsBoolean());
        for (Accepting entry : accepting) {
            entry.autoRead.accept(true);
        }
    }

    /**
     * Whether any socket is bound.
     *
     * @return True once a server asked for one
     */
    public synchronized boolean isBound() {
        return !sockets.isEmpty();
    }

    /**
     * Answers every connection with a 503 until a server accepts on the sockets again: no generation runs.
     *
     * @param reason Why, the body of the response
     */
    public synchronized void serveUnavailable(String reason) {
        if (closed || sockets.isEmpty()) {
            return;
        }
        stopResponder();
        accepting.removeIf(entry -> !entry.open.getAsBoolean());
        if (!accepting.isEmpty()) {
            // a server still accepts on them
            return;
        }
        try {
            Responder started = new Responder(new ArrayList<>(sockets.values()), reason);
            started.start();
            responder = started;
        } catch (IOException e) {
            LOG.warn("Cannot answer requests while no generation runs: {}", e.getMessage(), e);
        }
    }

    /**
     * Stops answering with a 503: a generation runs again, and its servers accept on the sockets once they start.
     */
    public synchronized void stopServingUnavailable() {
        stopResponder();
    }

    @Override
    public synchronized void close() {
        closed = true;
        stopResponder();
        for (ServerSocketChannel socket : sockets.values()) {
            try {
                socket.close();
            } catch (IOException e) {
                LOG.debug("Cannot close {}", socket, e);
            }
        }
        sockets.clear();
        accepting.clear();
    }

    private void stopResponder() {
        Responder current = responder;
        if (current != null) {
            responder = null;
            current.stop();
        }
    }

    private record Accepting(ServerSocketChannel socket, Consumer<Boolean> autoRead, BooleanSupplier open) {
    }

    /**
     * Accepts on the sockets through a selector of its own, and answers each connection on a thread of its own,
     * once its request head is read, so that closing it does not reset the response the client is reading.
     */
    private static final class Responder implements Runnable {
        private final Selector selector;
        private final byte[] response;
        private final Thread thread;
        private volatile boolean stopped;

        Responder(List<ServerSocketChannel> sockets, String reason) throws IOException {
            selector = Selector.open();
            try {
                for (ServerSocketChannel socket : sockets) {
                    socket.register(selector, SelectionKey.OP_ACCEPT);
                }
            } catch (IOException | RuntimeException e) {
                selector.close();
                throw e;
            }
            byte[] body = ("The application is not running: " + reason + "\n").getBytes(StandardCharsets.UTF_8);
            response = ("HTTP/1.1 503 Service Unavailable\r\nRetry-After: 1\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "
                + body.length + "\r\nConnection: close\r\n\r\n" + new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
            thread = new Thread(this, "micronaut-dev-unavailable");
            thread.setDaemon(true);
        }

        void start() {
            thread.start();
        }

        void stop() {
            stopped = true;
            selector.wakeup();
            try {
                thread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                // deregisters the sockets, for the next server's selector to take them
                selector.close();
            } catch (IOException e) {
                LOG.debug("Cannot close the selector", e);
            }
        }

        @Override
        public void run() {
            try {
                while (!stopped) {
                    selector.select();
                    if (stopped) {
                        return;
                    }
                    for (SelectionKey key : selector.selectedKeys()) {
                        if (key.isValid() && key.isAcceptable()) {
                            SocketChannel connection = ((ServerSocketChannel) key.channel()).accept();
                            if (connection != null) {
                                Thread answer = new Thread(() -> answer(connection), "micronaut-dev-unavailable-connection");
                                answer.setDaemon(true);
                                answer.start();
                            }
                        }
                    }
                    selector.selectedKeys().clear();
                }
            } catch (IOException | ClosedSelectorException e) {
                if (!stopped) {
                    LOG.debug("Stopped answering requests while no generation runs", e);
                }
            }
        }

        private void answer(SocketChannel connection) {
            try (connection) {
                connection.configureBlocking(true);
                connection.socket().setSoTimeout(READ_TIMEOUT_MILLIS);
                InputStream in = connection.socket().getInputStream();
                int first = in.read();
                if (first == TLS_HANDSHAKE) {
                    // an HTTPS listener: a plaintext answer would be a protocol error, the client sees the connection close
                    return;
                }
                // the request head, so that closing the connection does not discard unread bytes and reset the response
                int matched = first == '\r' ? 1 : 0;
                for (int read = 1; first >= 0 && read < MAX_REQUEST_HEAD && matched < 4; read++) {
                    int b = in.read();
                    if (b < 0) {
                        break;
                    }
                    matched = b == (matched % 2 == 0 ? '\r' : '\n') ? matched + 1 : (b == '\r' ? 1 : 0);
                }
                connection.write(ByteBuffer.wrap(response));
                connection.shutdownOutput();
            } catch (IOException e) {
                LOG.debug("Cannot answer a request while no generation runs", e);
            }
        }
    }
}
