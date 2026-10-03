package io.micronaut.http.client.netty

import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * A raw HTTP/1.1 upstream on a server socket, to observe what a client does on the wire: the
 * bytes it sends, and whether it closes the connection. It never responds on its own.
 */
class RawSocketUpstream implements AutoCloseable {
    private final ServerSocket serverSocket
    private final BlockingQueue<Connection> connections = new LinkedBlockingQueue<>()
    private final List<Connection> all = Collections.synchronizedList(new ArrayList<Connection>())

    RawSocketUpstream() {
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        Thread acceptor = new Thread(this::accept, "raw-socket-upstream-acceptor")
        acceptor.daemon = true
        acceptor.start()
    }

    URI uri(String path) {
        return URI.create("http://127.0.0.1:" + serverSocket.localPort + path)
    }

    /**
     * @return The next connection the client opened, or {@code null} if it opened none in time
     */
    Connection nextConnection(long seconds) {
        return connections.poll(seconds, TimeUnit.SECONDS)
    }

    private void accept() {
        while (!serverSocket.closed) {
            try {
                Connection connection = new Connection(serverSocket.accept())
                all.add(connection)
                connections.add(connection)
                connection.start()
            } catch (IOException ignored) {
                return
            }
        }
    }

    @Override
    void close() {
        serverSocket.close()
        synchronized (all) {
            for (Connection connection : all) {
                connection.socket.close()
            }
        }
    }

    /**
     * @return A port nothing listens on
     */
    static int unusedPort() {
        new ServerSocket(0).withCloseable { it.localPort }
    }

    static class Connection {
        final CountDownLatch requestHeadersReceived = new CountDownLatch(1)
        final CountDownLatch closedByClient = new CountDownLatch(1)
        final AtomicLong bytesReceived = new AtomicLong()
        final Socket socket
        private final ByteArrayOutputStream receivedBytes = new ByteArrayOutputStream()

        Connection(Socket socket) {
            this.socket = socket
        }

        private void start() {
            Thread reader = new Thread(this::read, "raw-socket-upstream-reader")
            reader.daemon = true
            reader.start()
        }

        private void read() {
            byte[] buffer = new byte[8192]
            try {
                InputStream input = socket.inputStream
                int n
                while ((n = input.read(buffer)) != -1) {
                    synchronized (receivedBytes) {
                        receivedBytes.write(buffer, 0, n)
                    }
                    if (requestHeadersReceived.count > 0 && received().contains("\r\n\r\n")) {
                        requestHeadersReceived.countDown()
                    }
                    bytesReceived.addAndGet(n)
                }
            } catch (IOException ignored) {
                // reset by the client, or closed by the test
            }
            closedByClient.countDown()
        }

        void write(String response) {
            write(response.getBytes(StandardCharsets.ISO_8859_1))
        }

        void write(byte[] bytes) {
            OutputStream out = socket.outputStream
            out.write(bytes)
            out.flush()
        }

        /**
         * Write, ignoring that the client closed the connection.
         */
        void writeQuietly(String response) {
            try {
                write(response)
            } catch (IOException ignored) {
                // the client closed the connection
            }
        }

        String received() {
            synchronized (receivedBytes) {
                return receivedBytes.toString(StandardCharsets.ISO_8859_1)
            }
        }

        boolean awaitRequest(long seconds) {
            return requestHeadersReceived.await(seconds, TimeUnit.SECONDS)
        }

        boolean awaitClosed(long seconds) {
            return closedByClient.await(seconds, TimeUnit.SECONDS)
        }
    }
}
