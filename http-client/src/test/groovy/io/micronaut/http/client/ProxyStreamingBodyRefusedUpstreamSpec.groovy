package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.util.ResourceLeakDetector
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * A streaming server request that is proxied to an upstream that refuses the connection: the
 * client gets the error response, and the rest of the request body is discarded so that the
 * connection stays usable for the next request.
 */
class ProxyStreamingBodyRefusedUpstreamSpec extends Specification {
    private static final int CHUNK_SIZE = 16 * 1024
    private static final int CHUNKS = 64

    @Shared
    ResourceLeakDetector.Level previousLevel

    @Shared
    @AutoCleanup
    EmbeddedServer server

    void setupSpec() {
        previousLevel = ResourceLeakDetector.level
        ResourceLeakDetector.level = ResourceLeakDetector.Level.PARANOID
        int refusedPort
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.loopbackAddress)) {
            refusedPort = socket.localPort
        }
        server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                          : 'ProxyStreamingBodyRefusedUpstreamSpec',
            'refused.url'                        : "http://127.0.0.1:$refusedPort/upstream".toString(),
            'micronaut.server.max-request-size'  : '100MB',
        ])
    }

    void cleanupSpec() {
        ResourceLeakDetector.level = previousLevel
    }

    void "a chunked request proxied to a refused upstream gets the error, and the connection serves the next request"() {
        given:
        Socket socket = new Socket(server.host, server.port)
        socket.soTimeout = 10_000
        OutputStream out = socket.outputStream
        InputStream input = new BufferedInputStream(socket.inputStream)

        when: 'a chunked body larger than one buffer is sent, from another thread in case the server stops reading'
        CompletableFuture<Void> written = CompletableFuture.runAsync {
            out.write(("POST /refused-proxy/relay HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Content-Type: text/plain\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n").getBytes(StandardCharsets.US_ASCII))
            byte[] chunk = ("x" * CHUNK_SIZE).getBytes(StandardCharsets.US_ASCII)
            for (int i = 0; i < CHUNKS; i++) {
                out.write((Integer.toHexString(CHUNK_SIZE) + "\r\n").getBytes(StandardCharsets.US_ASCII))
                out.write(chunk)
                out.write("\r\n".getBytes(StandardCharsets.US_ASCII))
            }
            out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            out.flush()
        }
        Map<String, Object> first = readResponse(input)

        then: 'the proxy fails'
        first.status >= 500

        when: 'the rest of the body was taken, and the same connection is used again'
        written.get(10, TimeUnit.SECONDS)
        out.write("GET /refused-proxy/ping HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
        out.flush()
        Map<String, Object> second = readResponse(input)

        then:
        second.status == 200
        second.body == 'pong'

        cleanup:
        socket?.close()
    }

    private static Map<String, Object> readResponse(InputStream input) {
        String statusLine = readLine(input)
        int status = Integer.parseInt(statusLine.split(' ')[1])
        Map<String, String> headers = [:]
        String line
        while (!(line = readLine(input)).isEmpty()) {
            int colon = line.indexOf(':')
            headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim())
        }
        String body = ''
        if (headers['content-length'] != null) {
            byte[] bytes = input.readNBytes(Integer.parseInt(headers['content-length']))
            body = new String(bytes, StandardCharsets.UTF_8)
        } else if (headers['transfer-encoding'] == 'chunked') {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream()
            while (true) {
                int size = Integer.parseInt(readLine(input).trim(), 16)
                if (size == 0) {
                    readLine(input)
                    break
                }
                bytes.write(input.readNBytes(size))
                readLine(input)
            }
            body = bytes.toString(StandardCharsets.UTF_8)
        }
        return [status: status, headers: headers, body: body]
    }

    private static String readLine(InputStream input) {
        StringBuilder builder = new StringBuilder()
        int b
        while ((b = input.read()) != '\n') {
            if (b == -1) {
                throw new EOFException("Connection closed after: " + builder)
            }
            if (b != '\r') {
                builder.append((char) b)
            }
        }
        return builder.toString()
    }

    @Controller("/refused-proxy")
    @Requires(property = "spec.name", value = "ProxyStreamingBodyRefusedUpstreamSpec")
    static class RelayController {
        private final ProxyHttpClient proxyHttpClient
        private final URI refused

        RelayController(ProxyHttpClient proxyHttpClient, @io.micronaut.context.annotation.Value('${refused.url}') String refused) {
            this.proxyHttpClient = proxyHttpClient
            this.refused = URI.create(refused)
        }

        @Post(value = "/relay", consumes = MediaType.ALL)
        Publisher<MutableHttpResponse<?>> relay(HttpRequest<?> request) {
            return proxyHttpClient.proxy(request.mutate().uri(refused))
        }

        @Get(value = "/ping", produces = MediaType.TEXT_PLAIN)
        String ping() {
            return "pong"
        }
    }
}
