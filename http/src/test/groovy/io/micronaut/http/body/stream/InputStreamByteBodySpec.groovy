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
                readMayEnd.countDown()
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

        then:
        closed.await(10, TimeUnit.SECONDS)
        received.isEmpty()

        cleanup:
        pool.shutdown()
    }

    def "a stream that fails with an unchecked exception fails the subscriber and is closed"() {
        given:
        def failure = new IllegalStateException("boom")
        def closed = new CountDownLatch(1)
        def stream = new InputStream() {
            @Override
            int read() {
                throw failure
            }

            @Override
            int read(byte[] b, int off, int len) {
                throw failure
            }

            @Override
            void close() {
                closed.countDown()
            }
        }
        def body = InputStreamByteBody.create(stream, OptionalLong.empty(), Runnable::run, ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE))
        def received = []
        Subscription subscription = null

        when:
        body.toReadBufferPublisher().subscribe(recorder(received, { subscription = it }))
        subscription.request(1)

        then:
        received == [failure]
        closed.count == 0
    }

    def "a request for no arrays fails the subscriber and closes the stream"() {
        given:
        def closed = new CountDownLatch(1)
        def stream = new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)) {
            @Override
            void close() {
                closed.countDown()
            }
        }
        def body = InputStreamByteBody.create(stream, OptionalLong.empty(), Runnable::run, ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE))
        def received = []
        Subscription subscription = null

        when:
        body.toReadBufferPublisher().subscribe(recorder(received, { subscription = it }))
        subscription.request(0)

        then:
        received.size() == 1
        received[0] instanceof IllegalArgumentException
        closed.count == 0
    }

    def "executor rejection fails the subscriber and closes the stream"() {
        given:
        def closed = new CountDownLatch(1)
        def failure = new java.util.concurrent.RejectedExecutionException("shutdown")
        def stream = new ByteArrayInputStream(new byte[1]) {
            @Override
            void close() {
                closed.countDown()
            }
        }
        def body = InputStreamByteBody.create(stream, OptionalLong.empty(), { throw failure }, ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE))
        def received = []
        Subscription subscription = null
        body.toReadBufferPublisher().subscribe(recorder(received, { subscription = it }))

        when:
        subscription.request(1)
        subscription.request(1)
        subscription.cancel()

        then:
        received == [failure]
        closed.count == 0
    }

    private static Subscriber<ReadBuffer> recorder(List<Object> received, Closure onSubscribe) {
        return new Subscriber<ReadBuffer>() {
            @Override
            void onSubscribe(Subscription s) {
                onSubscribe.call(s)
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
        }
    }
}
