package io.micronaut.http.server.netty.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.core.type.MutableHeaders
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.body.MessageBodyWriter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The elements of a streamed response are serialized on the I/O executor when their writer is
 * blocking. When the client goes away while an element is being serialized, the response is
 * discarded on the event loop, which must not wait for that serialization to finish.
 */
class BlockingStreamWriterCancelSpec extends Specification {

    void "discarding a streamed response does not wait for a blocking writer"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                  : 'BlockingStreamWriterCancelSpec',
                // one event loop thread: a blocked event loop blocks every connection
                'micronaut.netty.event-loops.default.num-threads': '1',
        ])
        SlowWriter writer = server.applicationContext.getBean(SlowWriter)
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when: 'a client reads the start of the stream and goes away while the next element is being written'
        Socket socket = new Socket(server.host, server.port)
        socket.outputStream.write("GET /blocking-stream HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
        socket.outputStream.flush()
        def head = new StringBuilder()
        def input = socket.inputStream
        while (!head.contains("first")) {
            int b = input.read()
            assert b != -1
            head.append((char) b)
        }
        assert writer.writing.await(10, TimeUnit.SECONDS)
        socket.close()

        then: 'the event loop still serves other requests while the writer is blocked'
        client.toBlocking().retrieve("/blocking-stream/ping") == "pong"
        writer.release.count == 1

        cleanup:
        writer?.release?.countDown()
        writer?.done?.await(10, TimeUnit.SECONDS)
        client?.close()
        server?.close()
    }

    static class Slow {
        final boolean block

        Slow(boolean block) {
            this.block = block
        }
    }

    @Requires(property = 'spec.name', value = 'BlockingStreamWriterCancelSpec')
    @Singleton
    @Produces("application/x-slow")
    static class SlowWriter implements MessageBodyWriter<Slow> {
        final CountDownLatch writing = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        final CountDownLatch done = new CountDownLatch(1)

        @Override
        boolean isBlocking() {
            return true
        }

        @Override
        void writeTo(Argument<Slow> type, MediaType mediaType, Slow object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            if (!object.block) {
                outputStream.write("first".getBytes(StandardCharsets.US_ASCII))
                return
            }
            writing.countDown()
            try {
                release.await(30, TimeUnit.SECONDS)
                outputStream.write("second".getBytes(StandardCharsets.US_ASCII))
            } finally {
                done.countDown()
            }
        }
    }

    @Requires(property = 'spec.name', value = 'BlockingStreamWriterCancelSpec')
    @Controller("/blocking-stream")
    static class StreamController {

        @Get(produces = "application/x-slow")
        Flux<Slow> stream() {
            return Flux.just(new Slow(false), new Slow(true))
        }

        @Get(value = "/ping", produces = MediaType.TEXT_PLAIN)
        String ping() {
            return "pong"
        }
    }
}
