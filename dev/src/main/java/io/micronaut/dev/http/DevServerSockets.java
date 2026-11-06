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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The listening sockets of the application's HTTP servers, bound once and kept for the life of the
 * development runtime, so that the port stays bound across generations: a connection that arrives while
 * one generation stops and the next starts waits in the socket's backlog, and the next generation's
 * server accepts it, instead of being refused.
 *
 * <p>While a batch is processed, the servers stop accepting, so that a new connection is served by
 * whichever generation runs once the batch is done. While no generation runs because the last one failed
 * to start, the sockets answer every connection with a generic 503 and a {@code Retry-After}, rather than
 * leaving it waiting; why it failed is in the console only. A request for a host the development runtime does not
 * answer is refused with a 403, and only a few connections are answered at once, each on a thread of its own: others
 * are closed.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class DevServerSockets implements AutoCloseable {

    /**
     * How many connections are answered at once while no generation runs: each is read on a thread of its own, for up
     * to two seconds, and a peer opening connections must not exhaust the process's threads.
     */
    static final int MAX_ANSWERING = 8;
    static final String UNAVAILABLE_THREAD = "micronaut-dev-unavailable-connection";
    static final String UNAVAILABLE_BODY = "The application is not running: its last start failed. See the console for why; the next change restarts it.\n";
    static final String FORBIDDEN_BODY = "Development mode: this host is not allowed.\n";

    private static final Logger LOG = LoggerFactory.getLogger(DevServerSockets.class);
    private static final int BACKLOG = 1024;
    private static final int MAX_REQUEST_HEAD = 64 * 1024;
    private static final int READ_TIMEOUT_MILLIS = 2_000;
    /**
     * The first byte of a TLS record carrying a handshake, a ClientHello.
     */
    private static final int TLS_HANDSHAKE = 0x16;

    private final Map<String, ServerSocketChannel> sockets = new LinkedHashMap<>();
    private final Predicate<@Nullable String> allowsHost;
    private final List<Accepting> accepting = new ArrayList<>();
    /**
     * The sockets handed to a server that has not accepted on them yet, with the number of that handout: its
     * generation is starting, and binds them a moment after its context starts, so they are not a dropped listener's.
     */
    private final Map<ServerSocketChannel, Long> handedOut = new HashMap<>();
    private long handouts;
    private boolean closed;
    private @Nullable Responder responder;

    /**
     * Sockets whose 503 answers only {@code localhost}, its subdomains and the loopback addresses.
     */
    public DevServerSockets() {
        this(host -> {
            String name = host == null ? null : io.micronaut.dev.DevAccess.hostName(host);
            return name != null && io.micronaut.dev.DevAccess.isLoopback(name);
        });
    }

    /**
     * @param allowsHost Whether the 503 answers a request's {@code Host}, null when it has none
     */
    public DevServerSockets(Predicate<@Nullable String> allowsHost) {
        this.allowsHost = allowsHost;
    }

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
        handedOut.put(socket, ++handouts);
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
        handedOut.remove(socket);
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
            // a socket the running generation does not accept on belongs to a listener its configuration no longer has;
            // one a server of a generation that is still starting was handed is about to be claimed
            release(0);
        }
        for (Accepting entry : accepting) {
            if (entry.open.getAsBoolean()) {
                entry.autoRead.accept(false);
            }
        }
    }

    /**
     * The number of sockets handed out so far, to pass to {@link #releaseUnclaimed(long)} once the generation they
     * were handed to has started.
     *
     * @return The mark
     */
    public synchronized long handoutMark() {
        return handouts;
    }

    /**
     * Closes the sockets no server accepts on: their listener is gone from the running generation's configuration.
     */
    public synchronized void releaseUnclaimed() {
        releaseUnclaimed(handouts);
    }

    /**
     * Closes the sockets no server accepts on, including those handed out up to a mark and not claimed: the
     * generation they were handed to has started, and its servers are not going to claim them. A socket handed out
     * after the mark belongs to a newer generation that is still starting, and is kept.
     *
     * @param mark The {@link #handoutMark()} taken before waiting for the generation's servers
     */
    public synchronized void releaseUnclaimed(long mark) {
        release(mark);
    }

    /**
     * As {@link #releaseUnclaimed(long)}, only while the generation that was awaited is still the current one: once a
     * newer generation exists, the sockets are its own.
     *
     * @param mark The {@link #handoutMark()} taken before waiting for the generation's servers
     * @param current Whether the awaited generation is still the current one
     */
    public synchronized void releaseUnclaimed(long mark, BooleanSupplier current) {
        if (current.getAsBoolean()) {
            release(mark);
        }
    }

    private void release(long handedOutUpTo) {
        accepting.removeIf(entry -> !entry.open.getAsBoolean());
        sockets.values().removeIf(socket -> {
            Long handout = handedOut.get(socket);
            if (isAccepting(socket) || handout != null && handout > handedOutUpTo) {
                return false;
            }
            handedOut.remove(socket);
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
     */
    public synchronized void serveUnavailable() {
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
            Responder started = new Responder(new ArrayList<>(sockets.values()), allowsHost);
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

    /**
     * As {@link #stopServingUnavailable()}, only while a started generation is still the current one: a newer one that
     * failed to start answers with a 503 until the next one runs.
     *
     * @param current Whether the started generation is still the current one
     * @return Whether it is
     */
    public synchronized boolean stopServingUnavailable(BooleanSupplier current) {
        if (!current.getAsBoolean()) {
            return false;
        }
        stopResponder();
        return true;
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
        handedOut.clear();
    }

    private void stopResponder() {
        Responder current = responder;
        if (current != null) {
            responder = null;
            current.stop();
        }
    }

    /**
     * The {@code Host} of a request head, read as ISO-8859-1, or null when it has none or more than one.
     */
    static @Nullable String host(CharSequence head) {
        String host = null;
        String[] lines = head.toString().split("\r\n");
        // the first line is the request line
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().toLowerCase(Locale.ROOT).equals("host")) {
                if (host != null) {
                    return null;
                }
                host = line.substring(colon + 1).trim();
            }
        }
        return host;
    }

    private record Accepting(ServerSocketChannel socket, Consumer<Boolean> autoRead, BooleanSupplier open) {
    }

    /**
     * Accepts on the sockets through a selector of its own, and answers each connection on a thread of its own,
     * once its request head is read, so that closing it does not reset the response the client is reading.
     */
    private static final class Responder implements Runnable {
        private static final byte[] UNAVAILABLE = response("503 Service Unavailable", "Retry-After: 1\r\n", UNAVAILABLE_BODY);
        private static final byte[] FORBIDDEN = response("403 Forbidden", "", FORBIDDEN_BODY);

        private final Selector selector;
        private final Predicate<@Nullable String> allowsHost;
        private final Semaphore answering = new Semaphore(MAX_ANSWERING);
        private final Thread thread;
        private volatile boolean stopped;

        Responder(List<ServerSocketChannel> sockets, Predicate<@Nullable String> allowsHost) throws IOException {
            this.allowsHost = allowsHost;
            selector = Selector.open();
            try {
                for (ServerSocketChannel socket : sockets) {
                    socket.register(selector, SelectionKey.OP_ACCEPT);
                }
            } catch (IOException | RuntimeException e) {
                selector.close();
                throw e;
            }
            thread = new Thread(this, "micronaut-dev-unavailable");
            thread.setDaemon(true);
        }

        private static byte[] response(String status, String headers, String text) {
            byte[] body = text.getBytes(StandardCharsets.UTF_8);
            return ("HTTP/1.1 " + status + "\r\n" + headers + "Content-Type: text/plain; charset=utf-8\r\nContent-Length: "
                + body.length + "\r\nConnection: close\r\n\r\n" + text).getBytes(StandardCharsets.UTF_8);
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
                                answerLater(connection);
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

        /**
         * Answers a connection on a thread of its own, when fewer than {@link #MAX_ANSWERING} are being answered;
         * otherwise closes it: the client retries, as it would a refused connection.
         */
        private void answerLater(SocketChannel connection) {
            if (!answering.tryAcquire()) {
                close(connection);
                return;
            }
            try {
                Thread answer = new Thread(() -> {
                    try {
                        answer(connection);
                    } finally {
                        answering.release();
                    }
                }, UNAVAILABLE_THREAD);
                answer.setDaemon(true);
                answer.start();
            } catch (RuntimeException | Error e) {
                answering.release();
                close(connection);
                throw e;
            }
        }

        private static void close(SocketChannel connection) {
            try {
                connection.close();
            } catch (IOException e) {
                LOG.debug("Cannot close a connection", e);
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
                StringBuilder head = new StringBuilder();
                if (first >= 0) {
                    head.append((char) first);
                }
                int matched = first == '\r' ? 1 : 0;
                for (int read = 1; first >= 0 && read < MAX_REQUEST_HEAD && matched < 4; read++) {
                    int b = in.read();
                    if (b < 0) {
                        break;
                    }
                    head.append((char) b);
                    matched = b == (matched % 2 == 0 ? '\r' : '\n') ? matched + 1 : (b == '\r' ? 1 : 0);
                }
                byte[] response = allowsHost.test(host(head)) ? UNAVAILABLE : FORBIDDEN;
                ByteBuffer buffer = ByteBuffer.wrap(response);
                while (buffer.hasRemaining()) {
                    connection.write(buffer);
                }
                connection.shutdownOutput();
            } catch (IOException e) {
                LOG.debug("Cannot answer a request while no generation runs", e);
            }
        }
    }
}
