package io.micronaut.http.body.stream

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.core.io.buffer.ReadBuffer
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InputStreamByteBodySpec extends Specification {
    def move() {
        given:
        def pool = Executors.newCachedThreadPool()
        def a = InputStreamByteBody.create(new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8)), OptionalLong.empty(), pool, ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE))
        def b = a.move()

        when:
        a.close()
        then:
        b.buffer().get().toString(StandardCharsets.UTF_8) == "foo"

        cleanup:
        pool.shutdown()
    }

    def "an array whose read was running when the subscription was cancelled is not delivered"() {
        given:
        def pool = Executors.newCachedThreadPool()
        def readStarted = new CountDownLatch(1)
        def readMayEnd = new CountDownLatch(1)
        def closed = new CountDownLatch(1)
        def stream = new InputStream() {
            @Override
            int read() {
                throw new UnsupportedOperationException()
            }

            @Override
            int read(byte[] b, int off, int len) {
                readStarted.countDown()
                readMayEnd.await(10, TimeUnit.SECONDS)
                b[off] = (byte) 'x'
                return 1
            }

            @Override
            void close() {
                closed.countDown()
            }
        }
        def body = InputStreamByteBody.create(stream, OptionalLong.empty(), pool, ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE))
        def received = new CopyOnWriteArrayList()
        Subscription subscription = null

        when:
        body.toReadBufferPublisher().subscribe(new Subscriber<ReadBuffer>() {
            @Override
            void onSubscribe(Subscription s) {
                subscription = s
            }

            @Override
            void onNext(ReadBuffer readBuffer) {
                received.add(readBuffer)
            }

            @Override
            void onError(Throwable t) {
                received.add(t)
            }

            @Override
            void onComplete() {
                received.add("complete")
            }
        })
        subscription.request(1)
        readStarted.await(10, TimeUnit.SECONDS)
        subscription.cancel()
        readMayEnd.countDown()

        then:
        closed.await(10, TimeUnit.SECONDS)
        received.isEmpty()

        cleanup:
        pool.shutdown()
    }
}
