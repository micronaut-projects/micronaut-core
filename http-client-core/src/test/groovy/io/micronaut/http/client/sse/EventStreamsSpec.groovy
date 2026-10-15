package io.micronaut.http.client.sse

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.body.BodyElements
import io.micronaut.json.JsonMapper
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

class EventStreamsSpec extends Specification {
    void "piece events use the mapper supplied by the caller"() {
        given:
        JsonMapper mapper = Mock()
        Supplier<JsonMapper> provider = Mock()
        def type = Argument.of(Integer)
        def response = HttpResponse.ok(pieces()).contentType(MediaType.TEXT_EVENT_STREAM_TYPE)

        when:
        def events = EventStreams.response(response, type, 1024, provider).body()
        def event = events.next().toCompletableFuture().join().orElseThrow()
        events.close()

        then:
        1 * provider.get() >> mapper
        1 * mapper.readValue(_ as byte[], type) >> 42
        event.data == 42
    }

    void "string events do not create a JSON mapper"() {
        given:
        Supplier<JsonMapper> provider = Mock()
        def response = HttpResponse.ok(pieces()).contentType(MediaType.TEXT_EVENT_STREAM_TYPE)

        when:
        def events = EventStreams.response(response, Argument.STRING, 1024, provider).body()
        def event = events.next().toCompletableFuture().join().orElseThrow()
        events.close()

        then:
        0 * provider.get()
        event.data == '42'
    }

    private static BodyElements pieces() {
        def buffer = ByteArrayBufferFactory.INSTANCE.wrap('data: 42\n\n'.getBytes(StandardCharsets.UTF_8))
        return BodyElements.of(() -> CompletableFuture.completedFuture(Optional.of(buffer)))
    }
}
