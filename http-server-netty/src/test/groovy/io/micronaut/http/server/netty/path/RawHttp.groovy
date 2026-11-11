package io.micronaut.http.server.netty.path

import java.nio.charset.StandardCharsets

/**
 * Sends a raw HTTP/1.1 request and returns the whole response text (or the head for an upgrade).
 */
class RawHttp {
    static String send(int port, String request, boolean headOnly = false) {
        Socket socket = new Socket("127.0.0.1", port)
        try {
            socket.soTimeout = 10_000
            socket.outputStream.write(request.getBytes(StandardCharsets.ISO_8859_1))
            socket.outputStream.flush()
            def out = new ByteArrayOutputStream()
            def buf = new byte[8192]
            try {
                int n
                while ((n = socket.inputStream.read(buf)) != -1) {
                    out.write(buf, 0, n)
                    if (headOnly && out.toString(StandardCharsets.ISO_8859_1.name()).contains("\r\n\r\n")) {
                        break
                    }
                }
            } catch (SocketTimeoutException ignored) {
            }
            return out.toString(StandardCharsets.ISO_8859_1.name())
        } finally {
            socket.close()
        }
    }

    static String get(int port, String path, Map<String, String> headers = [:]) {
        def sb = new StringBuilder("GET ${path} HTTP/1.1\r\n")
        if (!headers.containsKey('Host')) {
            sb.append("Host: localhost:${port}\r\n")
        }
        headers.each { k, v -> sb.append("${k}: ${v}\r\n") }
        sb.append("Connection: close\r\n\r\n")
        return send(port, sb.toString())
    }

    static String statusLine(String response) {
        int i = response.indexOf("\r\n")
        return i < 0 ? response : response.substring(0, i)
    }
}
