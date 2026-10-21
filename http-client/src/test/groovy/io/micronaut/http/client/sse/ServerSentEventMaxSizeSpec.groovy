package io.micronaut.http.client.sse

import com.sun.net.httpserver.HttpServer
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.exceptions.ContentLengthExceededException
import io.micronaut.http.sse.Event
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ServerSentEventMaxSizeSpec extends Specification {

    static final int MAX_CONTENT_LENGTH = 1024 * 1024

    @Shared HttpServer server
    @Shared @AutoCleanup ApplicationContext ctx
    @Shared SseClient sseClient

    def setupSpec() {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/unterminated") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            byte[] line = ("data: " + ("a" * 1000) + "\n").getBytes(StandardCharsets.UTF_8)
            try (OutputStream os = exchange.responseBody) {
                // far more than the client's limit, and never an empty line terminating the event
                for (int i = 0; i < 5000; i++) {
                    os.write(line)
                }
            } catch (IOException ignored) {
                // client closed the connection
            }
        }
        server.createContext("/small") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            try (OutputStream os = exchange.responseBody) {
                for (int i = 0; i < 10; i++) {
                    os.write(("data: " + ("b" * 1000) + "\n").getBytes(StandardCharsets.UTF_8))
                }
                os.write("\n".getBytes(StandardCharsets.UTF_8))
            }
        }
        server.start()
        ctx = ApplicationContext.run(['micronaut.http.client.max-content-length': MAX_CONTENT_LENGTH])
        sseClient = ctx.createBean(SseClient, new URL("http://127.0.0.1:${server.address.port}"))
    }

    def cleanupSpec() {
        server?.stop(0)
        (server?.executor as ExecutorService)?.shutdownNow()
    }

    void "event larger than max-content-length fails"() {
        when:
        Flux.from(sseClient.eventStream(HttpRequest.GET("/unterminated"))).collectList().block()

        then:
        def e = thrown(ContentLengthExceededException)
        e.message.contains("maximum allowed content length [" + MAX_CONTENT_LENGTH + "]")
    }

    void "multi-line event below max-content-length is received"() {
        when:
        List<Event<ByteBuffer<?>>> events = Flux.from(sseClient.eventStream(HttpRequest.GET("/small"))).collectList().block()

        then:
        events.size() == 1
        events[0].data.toString(StandardCharsets.UTF_8) == "b" * 10000
    }
}
