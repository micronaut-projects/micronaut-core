package io.micronaut.http.server.netty.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.concurrent.ThreadLocalRandom

class StreamPressureSpec extends Specification {
    def 'producer pressure'() {
        given:
        def data = new byte[1024 * 1024 * 4]
        ThreadLocalRandom.current().nextBytes(data)

        def ctx = ApplicationContext.run(['spec.name': 'StreamPressureSpec'])
        ctx.getBean(MyController).stream = new ByteArrayInputStream(data)

        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        expect:
        client.retrieve("/stream-pressure", byte[]) == data

        cleanup:
        server.stop()
        client.close()
        ctx.close()
    }

    def 'consumer pressure'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'StreamPressureSpec'])

        byte[] data = new byte[1024 * 1024]
        ThreadLocalRandom.current().nextBytes(data)
        def serverStream = new PipedOutputStream()
        ctx.getBean(MyController).stream = new PipedInputStream(serverStream)

        def clientOStream = new PipedOutputStream()
        def clientIStream = new PipedInputStream(clientOStream)

        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(StreamingHttpClient, server.URI)

        when:
        Flux.from(client.dataStream(HttpRequest.GET("/stream-pressure"))).subscribe {
            clientOStream.write(it.toByteArray())
        }
        serverStream.write(data)
        serverStream.flush()
        then:
        clientIStream.readNBytes(data.length) == data

        when:
        serverStream.write(data)
        serverStream.flush()
        then:
        clientIStream.readNBytes(data.length) == data

        cleanup:
        serverStream.close()
        clientIStream.close()
        server.stop()
        client.close()
        ctx.close()
    }

    private byte[] read(Iterator<ByteBuffer<?>> itr, int n) {
        byte[] out = new byte[n]
        int off = 0
        while (n > 0) {
            def buf = itr.next()
            def chunkN = buf.readableBytes()
            buf.read(out, off, chunkN)
            off += chunkN
            n -= chunkN
        }
        return out
    }

    @Requires(property = "spec.name", value = "StreamPressureSpec")
    @Controller
    static class MyController {
        InputStream stream

        @Get("/stream-pressure")
        InputStream get() {
            return this.stream
        }
    }
}
