package io.micronaut.http.client.bind

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.sse.Event
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

@Property(name = 'spec.name', value = 'ClientBindOnceSpec')
@MicronautTest
class ClientBindOnceSpec extends Specification {

    @Inject CountingClient client
    @Inject CountingBinder binder

    void setup() {
        binder.reset()
    }

    void "argument binders run once for a Mono<Void> client method"() {
        when:
        client.mono("abc").block()

        then:
        binder.count.get() == 1
        binder.lastValue == "abc"
    }

    void "argument binders run once for a client method returning a value"() {
        when:
        String result = client.value("hello").block()

        then:
        result == "hello"
        binder.count.get() == 1
    }

    void "argument binders run once for a client method returning a response"() {
        when:
        HttpResponse<String> response = client.response("resp").block()

        then:
        response.status() == HttpStatus.OK
        response.body() == "resp"
        binder.count.get() == 1
    }

    void "argument binders run once for a streaming client method"() {
        when:
        List<byte[]> chunks = Flux.from(client.stream("streamed")).collectList().block()

        then:
        chunks.collect { new String(it, StandardCharsets.UTF_8) }.join("") == "streamed"
        binder.count.get() == 1
    }

    void "argument binders run once for a streaming client method returning byte buffers"() {
        when:
        List<String> chunks = Flux.from(client.byteBufferStream("buffers"))
                .map { it.toString(StandardCharsets.UTF_8) }
                .collectList().block()

        then:
        chunks.join("") == "buffers"
        binder.count.get() == 1
    }

    void "argument binders run once for a JSON streaming client method"() {
        when:
        List<Map> result = Flux.from(client.jsonStream("json")).collectList().block()

        then:
        result == [[value: "json"]]
        binder.count.get() == 1
    }

    void "argument binders run once for an event streaming client method"() {
        when:
        List<Event<String>> events = Flux.from(client.eventStream("sse")).collectList().block()
        List<String> data = Flux.from(client.eventStreamData("sse")).collectList().block()

        then:
        events*.data == ["sse", "sse"]
        data == ["sse", "sse"]
        binder.count.get() == 2
    }

    void "a streaming client method with an unsupported item type fails"() {
        when:
        Flux.from(client.unsupportedStream("void")).blockLast()

        then:
        ConfigurationException e = thrown()
        e.message.contains("no TypeConverter from ByteBuffer")
        binder.count.get() == 1
    }

    void "a binding error is reported once through the returned publisher"() {
        when:
        client.rejected("abc", "bad").block()

        then:
        thrown(ConversionErrorException)
        binder.count.get() == 1
    }

    void "a binding error is reported once through the returned streaming publisher"() {
        when:
        Flux.from(client.rejectedStream("abc", "bad")).blockLast()

        then:
        thrown(ConversionErrorException)
        binder.count.get() == 1
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Bindable
    static @interface CountingHeader {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Bindable
    static @interface RejectedHeader {
    }

    @Requires(property = 'spec.name', value = 'ClientBindOnceSpec')
    @Singleton
    static class CountingBinder implements AnnotatedClientArgumentRequestBinder<CountingHeader> {

        final AtomicInteger count = new AtomicInteger()
        volatile String lastValue

        @Override
        Class<CountingHeader> getAnnotationType() {
            CountingHeader
        }

        @Override
        void bind(ArgumentConversionContext<Object> context, ClientRequestUriContext uriContext, Object value, MutableHttpRequest<?> request) {
            count.incrementAndGet()
            lastValue = value.toString()
            request.header("X-Count", value.toString())
        }

        void reset() {
            count.set(0)
            lastValue = null
        }
    }

    @Requires(property = 'spec.name', value = 'ClientBindOnceSpec')
    @Singleton
    static class RejectingBinder implements AnnotatedClientArgumentRequestBinder<RejectedHeader> {

        @Override
        Class<RejectedHeader> getAnnotationType() {
            RejectedHeader
        }

        @Override
        void bind(ArgumentConversionContext<Object> context, ClientRequestUriContext uriContext, Object value, MutableHttpRequest<?> request) {
            context.reject(value, new IllegalArgumentException("Rejected: " + value))
        }
    }

    @Requires(property = 'spec.name', value = 'ClientBindOnceSpec')
    @Client("/bind-once")
    static interface CountingClient {

        @Get("/void")
        Mono<Void> mono(@CountingHeader String value)

        @Get("/value")
        Mono<String> value(@CountingHeader String value)

        @Get("/value")
        Mono<HttpResponse<String>> response(@CountingHeader String value)

        @Get("/text")
        @Consumes(MediaType.TEXT_PLAIN)
        Publisher<byte[]> stream(@CountingHeader String value)

        @Get("/text")
        @Consumes(MediaType.TEXT_PLAIN)
        Publisher<ByteBuffer> byteBufferStream(@CountingHeader String value)

        @Get("/json")
        Publisher<Map> jsonStream(@CountingHeader String value)

        @Get("/sse")
        @Consumes(MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> eventStream(@CountingHeader String value)

        @Get("/sse")
        @Consumes(MediaType.TEXT_EVENT_STREAM)
        Publisher<String> eventStreamData(@CountingHeader String value)

        @Get("/void")
        @Consumes(MediaType.TEXT_PLAIN)
        Publisher<Void> unsupportedStream(@CountingHeader String value)

        @Get("/value")
        Mono<String> rejected(@CountingHeader String value, @RejectedHeader String rejected)

        @Get("/text")
        @Consumes(MediaType.TEXT_PLAIN)
        Publisher<byte[]> rejectedStream(@CountingHeader String value, @RejectedHeader String rejected)
    }

    @Requires(property = 'spec.name', value = 'ClientBindOnceSpec')
    @Controller("/bind-once")
    static class CountingController {

        @Get("/void")
        @Status(HttpStatus.OK)
        void noBody() {
            // the response has no body; the test only checks that the binder ran once
        }

        @Get("/value")
        String value(HttpRequest<?> request) {
            request.headers.get("X-Count")
        }

        @Get("/text")
        @Produces(MediaType.TEXT_PLAIN)
        String text(HttpRequest<?> request) {
            request.headers.get("X-Count")
        }

        @Get("/json")
        Publisher<Map<String, String>> json(HttpRequest<?> request) {
            Flux.just([value: request.headers.get("X-Count")])
        }

        @Get("/sse")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<String>> sse(HttpRequest<?> request) {
            String value = request.headers.get("X-Count")
            Flux.just(Event.of(value), Event.of(value))
        }
    }
}
