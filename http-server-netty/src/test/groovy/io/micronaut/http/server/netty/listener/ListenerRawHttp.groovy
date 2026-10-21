package io.micronaut.http.server.netty.listener

import java.nio.charset.StandardCharsets

/**
 * Raw socket helpers for the listener specs.
 */
class ListenerRawHttp {

    /**
     * @return the full response text, or null when the connection is refused
     */
    static String tryGet(int port, String path, int timeoutMs = 3000) {
        Socket socket = new Socket()
        try {
            socket.connect(new InetSocketAddress('127.0.0.1', port), timeoutMs)
        } catch (IOException ignored) {
            socket.close()
            return null
        }
        try {
            socket.soTimeout = timeoutMs
            socket.outputStream.write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            return readAll(socket.inputStream)
        } finally {
            socket.close()
        }
    }

    static String readAll(InputStream input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        byte[] buf = new byte[8192]
        try {
            int n
            while ((n = input.read(buf)) != -1) {
                out.write(buf, 0, n)
            }
        } catch (SocketTimeoutException | SocketException ignored) {
        }
        return out.toString(StandardCharsets.ISO_8859_1)
    }
}
