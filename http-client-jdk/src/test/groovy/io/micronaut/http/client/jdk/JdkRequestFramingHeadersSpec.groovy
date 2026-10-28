package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.ServerHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.RawHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The JDK client frames the request body itself and manages the connection, so like the Netty
 * client it must not send the framing headers and the {@code Connection} header of the request.
 * Otherwise the upstream can see both {@code Content-Length} and {@code Transfer-Encoding}, and
 * read the body as chunk framing.
 */
class JdkRequestFramingHeadersSpec extends Specification {
    static final String SPEC_NAME = 'JdkRequestFramingHeadersSpec'

    @Shared
    @AutoCleanup
    Upstream upstream = new Upstream()

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                       : SPEC_NAME,
            'framing-spec.upstream'           : "http://127.0.0.1:$upstream.port".toString(),
            // without an h2c upgrade, the JDK client sends no Connection and Upgrade of its own
            'micronaut.http.client.alpn-modes': ['http/1.1'],
    ])

    void "a raw exchange does not send the Transfer-Encoding of the request next to its Content-Length"() {
        given:
        RawHttpClient client = server.applicationContext.createBean(RawHttpClient)
        byte[] body = "0\r\n\r\nGET /internal HTTP/1.1\r\nHost: upstream\r\n\r\n".getBytes(StandardCharsets.UTF_8)

        when:
        HttpResponse<?> response = Mono.from(client.exchange(
                HttpRequest.POST("http://127.0.0.1:$upstream.port/raw-te", null)
                        .contentType(MediaType.TEXT_PLAIN_TYPE)
                        .header(HttpHeaders.TRANSFER_ENCODING, "chunked"),
                ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(body), null)).block()
        Received received = upstream.take()

        then:
        response.code() == 200
        received.path == "/raw-te"
        received.headers["content-length"] == [String.valueOf(body.length)]
        !received.headers.containsKey("transfer-encoding")
        received.body == body

        cleanup:
        response?.close()
        client?.close()
    }

    void "a raw exchange sends its own framing and Connection"() {
        given:
        RawHttpClient client = server.applicationContext.createBean(RawHttpClient)
        MutableHttpRequest<?> request = HttpRequest.POST("http://127.0.0.1:$upstream.port/raw", null)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                .header(HttpHeaders.CONTENT_LENGTH, "999")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-End-To-End", "kept")
        byte[] body = "0\r\n\r\nGET /internal HTTP/1.1\r\nHost: upstream\r\n\r\n".getBytes(StandardCharsets.UTF_8)

        when:
        HttpResponse<?> response = Mono.from(client.exchange(request,
                ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(body), null)).block()
        Received received = upstream.take()

        then:
        response.code() == 200
        received.path == "/raw"
        received.headers["content-length"] == [String.valueOf(body.length)]
        !received.headers.containsKey("transfer-encoding")
        !received.headers.containsKey("connection")
        received.headers["x-end-to-end"] == ["kept"]
        received.body == body

        cleanup:
        response?.close()
        client?.close()
    }

    void "a relayed chunked request reaches the upstream with a single framing"() {
        given:
        String payload = "0\r\n\r\nGET /internal HTTP/1.1\r\nHost: upstream\r\n\r\n"

        when:
        String response = sendChunked(server.port, "/relay", payload)
        Received received = upstream.take()

        then:
        response.startsWith("HTTP/1.1 200")
        received.path == "/relayed"
        // the gateway may relay the body with a known length or chunked, but never both
        (received.headers.containsKey("content-length")) != (received.headers.containsKey("transfer-encoding"))
        new String(received.body, StandardCharsets.UTF_8) == payload
        received.headers["x-end-to-end"] == ["kept"]
        // the client lists X-Forwarded-For in Connection, but the gateway sets it after copying
        received.headers["x-forwarded-for"] == ["gateway"]
    }

    void "the regular client sends its own framing and Connection"() {
        given:
        HttpClient client = server.applicationContext.createBean(HttpClient, new URL("http://127.0.0.1:$upstream.port"))

        when:
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.POST("/regular", "hello")
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                .header(HttpHeaders.CONTENT_LENGTH, "999")
                .header(HttpHeaders.CONNECTION, "keep-alive"))
        Received received = upstream.take()

        then:
        response.code() == 200
        received.path == "/regular"
        received.headers["content-length"] == ["5"]
        !received.headers.containsKey("transfer-encoding")
        !received.headers.containsKey("connection")
        new String(received.body, StandardCharsets.UTF_8) == "hello"

        cleanup:
        client?.close()
    }

    private static String sendChunked(int port, String path, String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8)
        new Socket("127.0.0.1", port).withCloseable { socket ->
            socket.soTimeout = 10_000
            OutputStream out = socket.outputStream
            out.write(("POST $path HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:$port\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "Connection: close\r\n" +
                    "Connection: X-Forwarded-For\r\n" +
                    "X-Forwarded-For: spoofed\r\n" +
                    "X-End-To-End: kept\r\n" +
                    "\r\n" +
                    Integer.toHexString(bytes.length) + "\r\n").getBytes(StandardCharsets.US_ASCII))
            out.write(bytes)
            out.write("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            out.flush()
            return new String(socket.inputStream.readAllBytes(), StandardCharsets.UTF_8)
        }
    }

    static class Received {
        String path
        Map<String, List<String>> headers
        byte[] body
    }

    /**
     * An HTTP/1.1 upstream on a plain socket that records the request head and body as they
     * arrive on the wire.
     */
    static class Upstream implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        final BlockingQueue<Received> received = new LinkedBlockingQueue<>()

        Upstream() {
            Thread.startDaemon("framing-spec-upstream") {
                while (!socket.closed) {
                    try {
                        socket.accept().withCloseable { handle(it) }
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        int getPort() {
            return socket.localPort
        }

        Received take() {
            Received r = received.poll(10, TimeUnit.SECONDS)
            assert r != null
            return r
        }

        private void handle(Socket connection) {
            connection.soTimeout = 10_000
            InputStream input = new BufferedInputStream(connection.inputStream)
            String requestLine = readLine(input)
            Map<String, List<String>> headers = [:]
            String line
            while (!(line = readLine(input)).isEmpty()) {
                int colon = line.indexOf(':')
                headers.computeIfAbsent(line.substring(0, colon).trim().toLowerCase(Locale.ROOT)) { [] }
                        .add(line.substring(colon + 1).trim())
            }
            byte[] body
            if (headers.containsKey("transfer-encoding")) {
                ByteArrayOutputStream decoded = new ByteArrayOutputStream()
                int size
                while ((size = Integer.parseInt(readLine(input).trim(), 16)) != 0) {
                    decoded.write(input.readNBytes(size))
                    readLine(input)
                }
                readLine(input)
                body = decoded.toByteArray()
            } else if (headers.containsKey("content-length")) {
                body = input.readNBytes(Integer.parseInt(headers["content-length"][0]))
            } else {
                body = new byte[0]
            }
            received.add(new Received(path: requestLine.split(" ")[1], headers: headers, body: body))
            OutputStream out = connection.outputStream
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            out.flush()
        }

        private static String readLine(InputStream input) {
            ByteArrayOutputStream line = new ByteArrayOutputStream()
            int b
            while ((b = input.read()) != '\n') {
                if (b == -1) {
                    throw new EOFException()
                }
                if (b != '\r') {
                    line.write(b)
                }
            }
            return line.toString(StandardCharsets.US_ASCII)
        }

        @Override
        void close() {
            socket.close()
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class GatewayController {
        private final RawHttpClient client
        private final String upstream

        GatewayController(RawHttpClient client, @io.micronaut.context.annotation.Value('${framing-spec.upstream}') String upstream) {
            this.client = client
            this.upstream = upstream
        }

        @Post(value = "/relay", consumes = MediaType.ALL, produces = MediaType.ALL)
        Publisher<? extends HttpResponse<?>> relay(ServerHttpRequest<?> request) {
            MutableHttpRequest<?> relayed = HttpRequest.POST(upstream + "/relayed", null)
            request.headers.forEach((name, values) -> {
                // the JDK client sets the Host header itself
                if (!name.equalsIgnoreCase(HttpHeaders.HOST)) {
                    values.forEach(value -> relayed.header(name, value))
                }
            })
            relayed.headers.set("X-Forwarded-For", "gateway")
            return client.exchange(relayed, request.byteBody().move(), null)
        }
    }
}
