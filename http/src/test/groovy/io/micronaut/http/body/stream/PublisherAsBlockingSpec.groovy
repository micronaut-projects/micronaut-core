package io.micronaut.http.body.stream

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.io.buffer.ReadBuffer
import io.micronaut.http.body.ByteBodyFactory
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class PublisherAsBlockingSpec extends Specification {
    def "an item delivered before onError is taken before the failure is reported"() {
        given:
        def blocking = new PublisherAsBlocking()
        def cause = new RuntimeException("boom")
        def factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
        // the publisher emits one piece and fails as soon as it is requested, before the reader takes it
        blocking.onSubscribe(new Subscription() {
            boolean emitted
            @Override
            void request(long n) {
                if (!emitted) {
                    emitted = true
                    blocking.onNext(factory.readBufferFactory().copyOf("foo", StandardCharsets.UTF_8))
                    blocking.onError(cause)
                }
            }
            @Override
            void cancel() {}
        })

        when:
        ReadBuffer first = blocking.take()
        then:
        first != null
        first.toString(StandardCharsets.UTF_8) == "foo"

        when:
        def second = blocking.take()
        then:
        second == null
        blocking.failure.is(cause)

        cleanup:
        first?.close()
        blocking.close()
    }

    def "an item pending at onError is released on close"() {
        given:
        def blocking = new PublisherAsBlocking()
        def released = false
        def buf = Mock(ReadBuffer) {
            close() >> { released = true }
        }
        blocking.onSubscribe(Mock(Subscription))

        when:
        blocking.onNext(buf)
        blocking.onError(new RuntimeException())
        then:
        !released

        when:
        blocking.close()
        then:
        released
    }

    def "an InputStream reads the data delivered before the failure, then throws"() {
        given:
        def factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
        def cause = new RuntimeException("boom")
        def publisher = { s ->
            s.onSubscribe(new Subscription() {
                boolean emitted
                @Override
                void request(long n) {
                    if (!emitted) {
                        emitted = true
                        s.onNext(factory.readBufferFactory().copyOf("foo", StandardCharsets.UTF_8))
                        s.onError(cause)
                    }
                }
                @Override
                void cancel() {}
            })
        } as org.reactivestreams.Publisher<ReadBuffer>
        def blocking = new PublisherAsBlocking()
        publisher.subscribe(blocking)
        def stream = new PublisherAsStream(blocking)
        def bytes = new byte[10]

        expect:
        stream.read(bytes) == 3
        new String(bytes, 0, 3, StandardCharsets.UTF_8) == "foo"

        when:
        stream.read(bytes)
        then:
        def e = thrown(IOException)
        e.cause.is(cause)

        cleanup:
        stream.close()
    }
}
