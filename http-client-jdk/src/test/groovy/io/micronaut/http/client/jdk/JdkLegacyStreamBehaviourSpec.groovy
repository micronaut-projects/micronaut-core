package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.io.buffer.ReferenceCounted
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.sse.SseClient
import io.micronaut.http.codec.CodecException
import io.micronaut.http.exceptions.BufferLengthExceededException
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * The publisher streams of the JDK client, which it shares with the Netty client, behave as the
 * Netty client's (see LegacyStreamBehaviourTest of the Netty client): an event stream is read as
 * one whatever its content type, a failure to decode the data of an event is the failure of the
 * reader, and the bytes of a data stream that wait for a slow subscriber are limited by
 * max-content-length. The pieces are byte array buffers, which hold nothing to release.
 */
class JdkLegacyStreamBehaviourSpec extends Specification {
    static final String SPEC = 'JdkLegacyStreamBehaviourSpec'
    static final Duration TIMEOUT = Duration.ofSeconds(10)
    static final String TWO_EVENTS = 'data: {"a":1}\n\ndata: {"a":2}\n\n'

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    @Shared
    @AutoCleanup
    ApplicationContext limitedContext = ApplicationContext.run(['micronaut.http.client.max-content-length': 1024])

    @Shared
    @AutoCleanup
    HttpClient limitedClient = limitedContext.createBean(HttpClient, server.URL)

    void "the client is the JDK client"() {
        expect:
        client instanceof JdkHttpClient
    }

    void "an event stream answered as JSON is read as an event stream"() {
        when:
        List<Event<Map>> events = Flux.from(((SseClient) client).eventStream(HttpRequest.GET('/jdk-legacy-behaviour/sse-as-json'), Map))
            .collectList().block(TIMEOUT)

        then:
        events*.data == [[a: 1], [a: 2]]
    }

    void "an untyped event stream answered as JSON is read as an event stream"() {
        when:
        List<String> events = Flux.from(((SseClient) client).eventStream(HttpRequest.GET('/jdk-legacy-behaviour/sse-as-json')))
            .map(event -> event.data.toString(StandardCharsets.UTF_8))
            .collectList().block(TIMEOUT)

        then:
        events == ['{"a":1}', '{"a":2}']
    }

    void "a failure to decode the data of a typed event is the failure of the reader"() {
        when:
        Flux.from(((SseClient) client).eventStream(HttpRequest.GET('/jdk-legacy-behaviour/sse-not-json'), Map)).blockLast(TIMEOUT)

        then:
        thrown(CodecException)
    }

    void "the pieces of a data stream are byte array buffers"() {
        when:
        List<ByteBuffer<?>> pieces = Flux.from(((StreamingHttpClient) client).dataStream(HttpRequest.GET('/jdk-legacy-behaviour/chunks?count=6&size=100&delay=10')))
            .collectList().block(TIMEOUT)

        then:
        pieces.sum { it.readableBytes() } == 600
        pieces.every { it.readableBytes() > 0 && !(it instanceof ReferenceCounted) }
    }

    void "a data stream larger than the content limit is read by a subscriber that keeps up"() {
        when:
        long total = Flux.from(((StreamingHttpClient) limitedClient).dataStream(HttpRequest.GET('/jdk-legacy-behaviour/chunks?count=10&size=512&delay=10')))
            .map(ByteBuffer::readableBytes)
            .reduce(0, Integer::sum)
            .block(TIMEOUT)

        then:
        total == 5120
    }

    void "the bytes of a data stream that wait for a slow subscriber are limited"() {
        when:
        Flux.from(((StreamingHttpClient) limitedClient).dataStream(HttpRequest.GET('/jdk-legacy-behaviour/chunks?count=10&size=512&delay=0&eager=10')))
            .delayElements(Duration.ofMillis(100), Schedulers.single())
            .map(ByteBuffer::readableBytes)
            .reduce(0, Integer::sum)
            .block(TIMEOUT)

        then:
        BufferLengthExceededException e = thrown()
        e.message.contains('exceeds the maximum allowed bufferable length [1024]')
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Controller('/jdk-legacy-behaviour')
    static class BehaviourController {

        @Get(uri = '/sse-as-json', produces = [MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM])
        HttpResponse<String> sseAsJson() {
            // the route accepts an event stream request, and answers JSON
            return HttpResponse.ok(TWO_EVENTS).contentType(MediaType.APPLICATION_JSON_TYPE)
        }

        @Get(uri = '/sse-not-json', produces = MediaType.TEXT_EVENT_STREAM)
        String sseNotJson() {
            return 'data: not json\n\n'
        }

        @Get(uri = '/chunks', produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> chunks(@QueryValue int count, @QueryValue int size, @QueryValue long delay, @QueryValue(defaultValue = '0') int eager) {
            int first = Math.min(eager, count)
            Flux<byte[]> eagerPieces = Flux.range(0, first).map(i -> ('a' * size).getBytes(StandardCharsets.UTF_8))
            Flux<byte[]> rest = Flux.range(0, count - first)
                .concatMap(i -> Mono.delay(Duration.ofMillis(delay)).thenReturn(('a' * size).getBytes(StandardCharsets.UTF_8)))
            return Flux.concat(eagerPieces, rest)
        }
    }
}
