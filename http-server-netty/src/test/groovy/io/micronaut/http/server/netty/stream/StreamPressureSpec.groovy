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
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import spock.util.concurrent.PollingConditions
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.io.ByteArrayOutputStream
import java.util.Arrays
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

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

    @IgnoreIf(value = { os.macOs }, reason = "seems to hang on macos")
    def 'consumer pressure'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'StreamPressureSpec'])
        def conditions = new PollingConditions(timeout: 5, delay: 0.1)

        byte[] data = new byte[1024 * 1024]
        ThreadLocalRandom.current().nextBytes(data)
        def serverStream = new PipedOutputStream()
        ctx.getBean(MyController).stream = new PipedInputStream(serverStream)

        def received = new ByteArrayOutputStream()
        def subscriptionReady = new CountDownLatch(1)
        def lock = new Object()
        Subscription subscription = null
        def subscriberError = null

        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(StreamingHttpClient, server.URI)

        when:
        Flux.from(client.dataStream(HttpRequest.GET("/stream-pressure"))).subscribe(new Subscriber<ByteBuffer<?>>() {
            @Override
            void onSubscribe(Subscription s) {
                subscription = s
                subscriptionReady.countDown()
                s.request(1)
            }

            @Override
            void onNext(ByteBuffer<?> byteBuffer) {
                synchronized (lock) {
                    received.write(byteBuffer.toByteArray())
                }
                subscription.request(1)
            }

            @Override
            void onError(Throwable t) {
                subscriberError = t
                subscriptionReady.countDown()
            }

            @Override
            void onComplete() {
            }
        })
        assert subscriptionReady.await(5, TimeUnit.SECONDS)
        serverStream.write(data)
        serverStream.flush()
        then:
        conditions.eventually {
            byte[] firstChunk
            synchronized (lock) {
                assert received.size() >= data.length
                firstChunk = Arrays.copyOfRange(received.toByteArray(), 0, data.length)
            }
            assert firstChunk == data
            assert subscriberError == null
        }

        when:
        serverStream.write(data)
        serverStream.flush()
        then:
        conditions.eventually {
            byte[] secondChunk
            synchronized (lock) {
                assert received.size() >= data.length * 2
                secondChunk = Arrays.copyOfRange(received.toByteArray(), data.length, data.length * 2)
            }
            assert secondChunk == data
            assert subscriberError == null
        }

        cleanup:
        serverStream.close()
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
