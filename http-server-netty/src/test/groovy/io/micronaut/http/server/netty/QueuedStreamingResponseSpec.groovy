package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.io.buffer.ByteBufferFactory
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A response pipelined behind a streaming response that is still being written, whose body already
 * holds bytes, must be released when the connection closes.
 */
class QueuedStreamingResponseSpec extends Specification {

    void "a queued #kind response body is released when the connection closes"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'QueuedStreamingResponseSpec'])
        def controller = server.applicationContext.getBean(QueuedController)
        Socket socket = new Socket(server.host, server.port)
        socket.soTimeout = 10_000

        when:
        socket.outputStream.write(("GET /queued/slow HTTP/1.1\r\nHost: localhost\r\n\r\n" + second).getBytes(StandardCharsets.UTF_8))
        socket.outputStream.flush()
        def reader = socket.inputStream
        byte[] first = new byte[12]
        int n = 0
        while (n < first.length) {
            n += reader.read(first, n, first.length - n)
        }

        then:
        new String(first, StandardCharsets.US_ASCII).startsWith("HTTP/1.1 200")
        controller.secondCalled.await(10, TimeUnit.SECONDS)

        when:
        socket.close()
        // give the server time to see the close
        Thread.sleep(1000)
        controller.slow.tryEmitComplete()
        Thread.sleep(500)

        then:
        kind != 'buffers' || controller.early.refCnt() == 0

        cleanup:
        socket.close()
        server.stop()

        where:
        kind      | second
        'buffers' | "GET /queued/buffered HTTP/1.1\r\nHost: localhost\r\n\r\n"
        'echo'    | "POST /queued/echo HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/octet-stream\r\nContent-Length: 10\r\n\r\n0123456789"
    }

    @Requires(property = 'spec.name', value = 'QueuedStreamingResponseSpec')
    @Controller('/queued')
    static class QueuedController {
        final Sinks.Many<ByteBuf> slow = Sinks.many().unicast().onBackpressureBuffer()
        final CountDownLatch secondCalled = new CountDownLatch(1)
        volatile ByteBuf early

        @Get(value = '/slow', produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<ByteBuf> slow() {
            slow.tryEmitNext(ByteBufAllocator.DEFAULT.directBuffer().writeBytes("first".getBytes(StandardCharsets.UTF_8)))
            return slow.asFlux()
        }

        @Get(value = '/buffered', produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<ByteBuf> buffered() {
            Sinks.Many<ByteBuf> sink = Sinks.many().unicast().onBackpressureBuffer()
            early = ByteBufAllocator.DEFAULT.directBuffer().writeBytes("early".getBytes(StandardCharsets.UTF_8))
            sink.tryEmitNext(early)
            secondCalled.countDown()
            return sink.asFlux()
        }

        @Post(value = '/echo', consumes = MediaType.APPLICATION_OCTET_STREAM, produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<ByteBuf> echo(@Body Publisher<ByteBuf> body) {
            secondCalled.countDown()
            return body
        }
    }
}
