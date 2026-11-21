package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.exceptions.ReadTimeoutException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The request timeout of the JDK client ends when the response headers arrive, so the raw
 * client applies the read timeout to the body it streams.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class JdkRawBodyReadTimeoutSpec extends Specification {

    void "a raw response body that stalls after the headers fails with a read timeout"() {
        given:
        def serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        def stall = new Object()
        Thread.start {
            try (Socket s = serverSocket.accept()) {
                readHead(s.inputStream)
                s.outputStream.write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n".getBytes(StandardCharsets.US_ASCII))
                s.outputStream.flush()
                synchronized (stall) {
                    stall.wait(30_000)
                }
            } catch (Exception ignored) {
            }
        }
        def ctx = ApplicationContext.run(['micronaut.http.client.read-timeout': '1s'])
        RawHttpClient client = ctx.createBean(RawHttpClient)

        when:
        def response = (ByteBodyHttpResponse<?>) Mono.from(client.exchange(HttpRequest.GET("http://127.0.0.1:$serverSocket.localPort/stall"), null, null)).block(Duration.ofSeconds(20))
        long start = System.nanoTime()
        Flux.from(response.byteBody().toByteArrayPublisher()).blockLast(Duration.ofSeconds(20))

        then:
        thrown(ReadTimeoutException)
        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 15

        cleanup:
        synchronized (stall) {
            stall.notifyAll()
        }
        response?.close()
        client?.close()
        ctx.close()
        serverSocket.close()
    }

    private static void readHead(InputStream input) {
        def sb = new StringBuilder()
        while (!sb.toString().endsWith("\r\n\r\n")) {
            int b = input.read()
            if (b == -1) {
                return
            }
            sb.append((char) b)
        }
    }
}
