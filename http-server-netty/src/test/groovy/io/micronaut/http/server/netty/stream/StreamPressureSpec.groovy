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
import org.reactivestreams.Subscription
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
        def serverStream = new ChunkQueueInputStream()
        ctx.getBean(MyController).stream = serverStream

        def clientStream = new ChunkQueueInputStream()
        def clientError = new AtomicReference<Throwable>()

        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(StreamingHttpClient, server.URI)

        when:
        // request one chunk at a time, and only when the test thread actually reads, so that the
        // client stops reading from the socket while the consumer is stalled and the server has
        // to apply backpressure
        Flux.from(client.dataStream(HttpRequest.GET("/stream-pressure"))).subscribe(new BaseSubscriber<ByteBuffer<?>>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                clientStream.demand = { subscription.request(1) }
            }

            @Override
            protected void hookOnNext(ByteBuffer<?> value) {
                clientStream.offer(value.toByteArray())
            }

            @Override
            protected void hookOnError(Throwable throwable) {
                clientError.set(throwable)
                clientStream.close()
            }

            @Override
            protected void hookOnComplete() {
                clientStream.close()
            }
        })
        serverStream.offer(data)
        then:
        clientStream.readNBytes(data.length) == data
        clientError.get() == null

        when:
        serverStream.offer(data)
        then:
        clientStream.readNBytes(data.length) == data
        clientError.get() == null

        cleanup:
        serverStream.close()
        clientStream.close()
        server.stop()
        client.close()
        ctx.close()
    }

    /**
     * A queue-backed {@link InputStream} that, unlike {@link PipedInputStream}, does not care which
     * thread reads from it. The server reads response streams on the blocking executor, which on
     * JDK 21+ is a thread-per-task virtual thread executor, so every read may happen on a fresh
     * thread that has died by the time the next chunk is offered.
     */
    private static class ChunkQueueInputStream extends InputStream {
        private static final byte[] EOF = new byte[0]

        private final BlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>()
        private byte[] current
        private int position
        /**
         * Invoked whenever the reader runs out of buffered data and is about to wait for the next
         * chunk.
         */
        Runnable demand = {}

        void offer(byte[] chunk) {
            chunks.add(chunk)
        }

        @Override
        synchronized int read() throws IOException {
            byte[] b = new byte[1]
            return read(b, 0, 1) == -1 ? -1 : b[0] & 0xff
        }

        @Override
        synchronized int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0
            }
            while (current == null || position == current.length) {
                if (current != null && current.is(EOF)) {
                    return -1
                }
                demand.run()
                byte[] next
                try {
                    next = chunks.poll(30, TimeUnit.SECONDS)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt()
                    throw new InterruptedIOException()
                }
                if (next == null) {
                    throw new IOException("Timed out waiting for the next chunk")
                }
                current = next
                position = 0
            }
            int n = Math.min(len, current.length - position)
            System.arraycopy(current, position, b, off, n)
            position += n
            return n
        }

        @Override
        void close() {
            chunks.add(EOF)
        }
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
