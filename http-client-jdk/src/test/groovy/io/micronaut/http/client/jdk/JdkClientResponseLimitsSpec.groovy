package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Timeout(value = 120, unit = TimeUnit.SECONDS)
class JdkClientResponseLimitsSpec extends Specification {

    ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())

    void cleanup() {
        serverSocket.close()
    }

    private void serve(Closure<?> handler) {
        Thread.start {
            while (!serverSocket.closed) {
                Socket s
                try {
                    s = serverSocket.accept()
                } catch (IOException ignored) {
                    return
                }
                Thread.start {
                    try {
                        readHead(s.inputStream)
                        handler(s)
                    } catch (Exception ignored) {
                    } finally {
                        s.close()
                    }
                }
            }
        }
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

    private URI getUri() {
        URI.create("http://127.0.0.1:" + serverSocket.localPort)
    }

    def "the buffering client applies max-content-length"() {
        given:
        byte[] body = ("x" * (2 * 1024 * 1024)).getBytes(StandardCharsets.US_ASCII)
        serve { Socket s ->
            s.outputStream.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            for (int i = 0; i < body.length; i += 8192) {
                s.outputStream.write(("2000\r\n").getBytes(StandardCharsets.US_ASCII))
                s.outputStream.write(body, i, 8192)
                s.outputStream.write("\r\n".getBytes(StandardCharsets.US_ASCII))
            }
            s.outputStream.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            s.outputStream.flush()
        }
        def ctx = ApplicationContext.run(['micronaut.http.client.max-content-length': '100000'])
        def client = ctx.createBean(HttpClient, uri)

        when:
        client.toBlocking().exchange("/big", String)

        then:
        thrown(Exception)

        cleanup:
        client.close()
        ctx.close()
    }

    def "the buffering client times out a stalled response body"() {
        given:
        def released = new CompletableFuture<Void>()
        serve { Socket s ->
            s.outputStream.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 1000\r\n\r\npartial".getBytes(StandardCharsets.US_ASCII))
            s.outputStream.flush()
            // stall with the connection open
            try {
                released.get(60, TimeUnit.SECONDS)
            } catch (Exception ignored) {
            }
        }
        def ctx = ApplicationContext.run([
                'micronaut.http.client.read-timeout'   : '1s',
                'micronaut.http.client.request-timeout': '2s',
        ])
        def client = ctx.createBean(HttpClient, uri)

        when:
        def call = CompletableFuture.supplyAsync { client.toBlocking().exchange("/stall", String) }
        boolean finished
        long start = System.currentTimeMillis()
        try {
            call.get(15, TimeUnit.SECONDS)
            finished = true
        } catch (java.util.concurrent.TimeoutException ignored) {
            finished = false
        } catch (java.util.concurrent.ExecutionException ignored) {
            finished = true
        }

        then:
        finished

        cleanup:
        released.complete(null)
        call?.cancel(true)
        client.close()
        ctx.close()
    }
}
