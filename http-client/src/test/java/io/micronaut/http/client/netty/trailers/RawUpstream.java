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
package io.micronaut.http.client.netty.trailers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A raw HTTP/1.1 upstream on a server socket, to observe what a client does on the wire: the
 * bytes it sends, and whether it closes the connection. It never responds on its own.
 */
final class RawUpstream implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final BlockingQueue<Connection> connections = new LinkedBlockingQueue<>();
    private final Thread acceptor;
    private final boolean readOnlyRequestHead;

    RawUpstream() throws IOException {
        this(false);
    }

    /**
     * @param readOnlyRequestHead Whether each connection stops reading once the request headers
     *                            arrived, so that a client that sends a large body cannot finish
     *                            writing it
     */
    RawUpstream(boolean readOnlyRequestHead) throws IOException {
        this.readOnlyRequestHead = readOnlyRequestHead;
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        acceptor = new Thread(this::accept, "raw-upstream-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    URI uri(String path) {
        return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + path);
    }

    /**
     * @param seconds How long to wait
     * @return The next connection the client opened, or {@code null} if it opened none
     */
    Connection nextConnection(long seconds) throws InterruptedException {
        return connections.poll(seconds, TimeUnit.SECONDS);
    }

    private void accept() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                Connection connection = new Connection(socket, readOnlyRequestHead);
                connections.add(connection);
                connection.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        for (Connection connection : connections) {
            connection.socket.close();
        }
    }

    /**
     * One connection of the client.
     */
    static final class Connection {
        final CountDownLatch requestHeadersReceived = new CountDownLatch(1);
        final CountDownLatch closedByClient = new CountDownLatch(1);
        final AtomicLong bytesReceived = new AtomicLong();
        private final Socket socket;
        private final boolean readOnlyRequestHead;
        private final ByteArrayOutputStream received = new ByteArrayOutputStream();

        Connection(Socket socket, boolean readOnlyRequestHead) {
            this.socket = socket;
            this.readOnlyRequestHead = readOnlyRequestHead;
        }

        /**
         * Close the connection, as a server that goes away does.
         */
        void close() throws IOException {
            socket.close();
        }

        /**
         * Close this side of the connection, as a server does that has nothing more to say: the
         * client reads the end of the stream. Unlike {@link #close()}, this does not reset the
         * connection when the client sent bytes that were not read.
         */
        void shutdownOutput() throws IOException {
            socket.shutdownOutput();
        }

        private void start() {
            Thread reader = new Thread(this::read, "raw-upstream-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void read() {
            byte[] buffer = new byte[8192];
            try {
                InputStream in = socket.getInputStream();
                int n;
                while ((n = in.read(buffer)) != -1) {
                    synchronized (received) {
                        received.write(buffer, 0, n);
                    }
                    bytesReceived.addAndGet(n);
                    if (requestHeadersReceived.getCount() > 0 && received().contains("\r\n\r\n")) {
                        requestHeadersReceived.countDown();
                        if (readOnlyRequestHead) {
                            // leave the rest of the request unread, and the connection open
                            return;
                        }
                    }
                }
            } catch (SocketException e) {
                // reset by the client, or closed by the test
            } catch (IOException e) {
                // closed
            }
            closedByClient.countDown();
        }

        /**
         * Write raw response bytes.
         *
         * @param response The bytes, e.g. a status line and headers
         */
        void write(String response) throws IOException {
            write(response.getBytes(StandardCharsets.ISO_8859_1));
        }

        void write(byte[] bytes) throws IOException {
            OutputStream out = socket.getOutputStream();
            out.write(bytes);
            out.flush();
        }

        /**
         * @return Everything the client sent on this connection
         */
        String received() {
            synchronized (received) {
                return received.toString(StandardCharsets.ISO_8859_1);
            }
        }

        boolean awaitRequest(long seconds) throws InterruptedException {
            return requestHeadersReceived.await(seconds, TimeUnit.SECONDS);
        }

        boolean awaitClosed(long seconds) throws InterruptedException {
            return closedByClient.await(seconds, TimeUnit.SECONDS);
        }
    }
}
