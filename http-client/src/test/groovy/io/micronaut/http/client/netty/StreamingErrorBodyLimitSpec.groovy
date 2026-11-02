package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.exceptions.ContentLengthExceededException
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.charset.StandardCharsets
import java.time.Duration

@Timeout(90)
class StreamingErrorBodyLimitSpec extends Specification {

    def "a streaming error body buffered for the error type is bounded by max-content-length"() {
        given:
        byte[] chunk = ("400\r\n" + ("z" * 1024) + "\r\n").getBytes(StandardCharsets.US_ASCII)
        def serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        Thread.start {
            try (Socket s = serverSocket.accept()) {
                readHead(s.inputStream)
                s.outputStream.write("HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
                long start = System.currentTimeMillis()
                try {
                    while (System.currentTimeMillis() - start < 25_000) {
                        s.outputStream.write(chunk)
                        s.outputStream.flush()
                        Thread.sleep(5)
                    }
                } catch (IOException ignored) {
                }
            } catch (Exception ignored) {
            }
        }
        def ctx = ApplicationContext.run([
                'micronaut.http.client.max-content-length': '10000',
                'micronaut.http.client.read-timeout'      : '60s',
        ])
        def client = ctx.createBean(StreamingHttpClient, new URL("http://127.0.0.1:" + serverSocket.localPort))

        when:
        long begin = System.currentTimeMillis()
        Flux.from(client.dataStream(HttpRequest.GET("/err"), Argument.of(String))).blockLast(Duration.ofSeconds(40))

        then: "the client fails once the limit is reached, not when the endless body ends"
        thrown(ContentLengthExceededException)
        System.currentTimeMillis() - begin < 15_000

        cleanup:
        client.close()
        ctx.close()
        serverSocket.close()
    }

    private static void readHead(InputStream input) {
        def sb = new StringBuilder()
        while (!sb.toString().endsWith("\r\n\r\n")) {
            int b = input.read()
            if (b == -1) {
                throw new EOFException()
            }
            sb.append((char) b)
        }
    }
}
