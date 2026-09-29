package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.io.buffer.ReadBufferFactory
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.AvailableByteArrayBody
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * The JDK raw client owns the request body of an exchange: it releases it however the exchange
 * ends, also when the JDK client never reads it.
 */
class JdkRawRequestBodySpec extends Specification {
    static final String SPEC_NAME = 'JdkRawRequestBodySpec'

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME])

    void "the request body of a refused exchange is released"() {
        given:
        RawHttpClient client = server.applicationContext.createBean(RawHttpClient)
        Map body = body("hello")
        int port = new ServerSocket(0).withCloseable { it.localPort }

        when:
        Mono.from(client.exchange(HttpRequest.POST("http://127.0.0.1:$port/raw-body/echo", null), body.body, null)).block()

        then:
        thrown(HttpClientException)
        body.closed()

        cleanup:
        client?.close()
    }

    void "the request body of a GET, which the JDK client does not send, is released"() {
        given:
        RawHttpClient client = server.applicationContext.createBean(RawHttpClient)
        Map body = body("ignored")

        when:
        ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(client.exchange(
            HttpRequest.GET(server.URI.toString() + "/raw-body/get"), body.body, null)).block()

        then:
        response.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == "get"
        body.closed()

        cleanup:
        response?.close()
        client?.close()
    }

    void "a missing request body is sent as an empty body"() {
        given:
        RawHttpClient client = server.applicationContext.createBean(RawHttpClient)

        when:
        ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(client.exchange(
            HttpRequest.POST(server.URI.toString() + "/raw-body/length", null).contentType(MediaType.TEXT_PLAIN_TYPE), null, null)).block()

        then:
        response.code() == 200
        response.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == "length=0"

        cleanup:
        response?.close()
        client?.close()
    }

    void "an empty request body is sent"() {
        given:
        RawHttpClient client = server.applicationContext.createBean(RawHttpClient)

        when:
        ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(client.exchange(
            HttpRequest.POST(server.URI.toString() + "/raw-body/length", null).contentType(MediaType.TEXT_PLAIN_TYPE),
            AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, new byte[0]), null)).block()

        then:
        response.code() == 200
        response.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == "length=0"

        cleanup:
        response?.close()
        client?.close()
    }

    void "the request body is released when the exchange cannot be built"() {
        given:
        RawHttpClient client = server.applicationContext.createBean(RawHttpClient)
        Map body = body("unsent")
        HttpRequest<?> request = Stub(HttpRequest) {
            toMutableRequest() >> { throw new IllegalStateException("cannot build") }
        }

        when:
        client.exchange(request, body.body, null)

        then:
        IllegalStateException e = thrown()
        e.message == "cannot build"
        body.closed()

        cleanup:
        client?.close()
    }

    void "a request body of unknown length is streamed and released"() {
        given:
        RawHttpClient client = server.applicationContext.createBean(RawHttpClient)
        Map body = body(ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(
            Flux.just(ReadBufferFactory.getJdkFactory().copyOf("streamed", StandardCharsets.UTF_8))))

        expect:
        !body.body.expectedLength().present

        when:
        ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(client.exchange(
            HttpRequest.POST(server.URI.toString() + "/raw-body/length", null).contentType(MediaType.TEXT_PLAIN_TYPE), body.body, null)).block()

        then:
        response.code() == 200
        response.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == "length=8"
        body.closed()

        cleanup:
        response?.close()
        client?.close()
    }

    private static Map body(String content) {
        return body(ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(content.getBytes(StandardCharsets.UTF_8)))
    }

    private static Map body(CloseableByteBody delegate) {
        boolean closed = false
        CloseableByteBody tracked = new CloseableByteBody() {
            @Delegate(excludes = ['close'])
            CloseableByteBody wrapped = delegate

            @Override
            void close() {
                closed = true
                wrapped.close()
            }
        }
        return [body: tracked, closed: { -> closed }]
    }

    @Controller("/raw-body")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RawBodyController {
        @Get(value = "/get", produces = MediaType.TEXT_PLAIN)
        String get() {
            return "get"
        }

        @Post(value = "/length", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String length(@Body @Nullable String body) {
            return "length=" + (body == null ? 0 : body.length())
        }
    }
}
