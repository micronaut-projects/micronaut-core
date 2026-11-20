package io.micronaut.http.body

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.http.MediaType
import io.micronaut.http.simple.SimpleHttpHeaders
import io.micronaut.runtime.ApplicationConfiguration
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class StringBodyReaderChunkedCompletionSpec extends Specification {

    void "a subscriber requesting exactly as many strings as there are chunks is completed"() {
        given:
        def reader = new StringBodyReader(new ApplicationConfiguration())
        Flux<ByteBuffer<?>> input = Flux.just(
                ByteArrayBufferFactory.INSTANCE.wrap('one'.getBytes(StandardCharsets.UTF_8)),
                ByteArrayBufferFactory.INSTANCE.wrap('two'.getBytes(StandardCharsets.UTF_8)))
        List<String> received = []
        boolean completed = false
        Throwable failure = null

        when:
        reader.readChunked(Argument.STRING, MediaType.TEXT_PLAIN_TYPE, new SimpleHttpHeaders(), input).subscribe(new Subscriber<String>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(2)
            }

            @Override
            void onNext(String s) {
                received << s
            }

            @Override
            void onError(Throwable t) {
                failure = t
            }

            @Override
            void onComplete() {
                completed = true
            }
        })

        then:
        failure == null
        received == ['one', 'two']
        completed
    }
}
