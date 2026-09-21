package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.exceptions.ContentLengthExceededException
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * A response whose declared Content-Length is over max-content-length is rejected as soon as the
 * headers are read. The client still has to consume the declared body, or the pooled connection
 * would be left with the rest of it and could not be reused for the next request.
 */
class MaxResponseSizeDiscardSpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13243')
    void "an oversized response with a known length is discarded and the connection reused"() {
        given: "a server that answers two requests on one connection"
        int bigLength = 5000
        AtomicInteger connections = new AtomicInteger()
        ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        Thread server = Thread.startVirtualThread {
            try {
                while (true) {
                    Socket socket = serverSocket.accept()
                    connections.incrementAndGet()
                    Thread.startVirtualThread {
                        try {
                            serve(socket, bigLength)
                        } catch (IOException ignored) {
                        } finally {
                            socket.close()
                        }
                    }
                }
            } catch (IOException ignored) {
                // closed
            }
        }
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.http.client.max-content-length': '1kb',
                'micronaut.http.client.read-timeout': '10s',
        ])
        HttpClient client = ctx.createBean(HttpClient, new URI("http://${serverSocket.inetAddress.hostAddress}:${serverSocket.localPort}"))

        when: "the first response is over the limit"
        client.toBlocking().retrieve(HttpRequest.GET('/big'), String)

        then: "it is rejected from its declared length"
        def e = thrown(ContentLengthExceededException)
        e.message.contains("The received length [${bigLength}] exceeds the maximum allowed content length [1024]")

        when: "the next request goes out"
        String small = client.toBlocking().retrieve(HttpRequest.GET('/small'), String)

        then: "it is answered on the same connection, whose body was drained"
        small == 'ok'
        connections.get() == 1

        cleanup:
        client.close()
        ctx.close()
        serverSocket.close()
        server.join(5000)
    }

    private static void serve(Socket socket, int bigLength) throws IOException {
        InputStream input = socket.inputStream
        OutputStream out = socket.outputStream
        while (true) {
            String requestLine = null
            String line
            // read the head, up to the blank line
            while ((line = readLine(input)) != null && !line.isEmpty()) {
                if (requestLine == null) {
                    requestLine = line
                }
            }
            if (requestLine == null) {
                return
            }
            byte[] body
            if (requestLine.startsWith('GET /big ')) {
                body = new byte[bigLength]
                Arrays.fill(body, (byte) 'x')
            } else {
                body = 'ok'.getBytes(StandardCharsets.US_ASCII)
            }
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${body.length}\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            out.write(body)
            out.flush()
        }
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream()
        int b
        while ((b = input.read()) != -1) {
            if (b == '\n') {
                break
            }
            if (b != '\r') {
                line.write(b)
            }
        }
        if (b == -1 && line.size() == 0) {
            return null
        }
        return line.toString(StandardCharsets.US_ASCII)
    }
}
