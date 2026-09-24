package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.core.type.MutableHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.body.MessageBodyWriter
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * A request whose body cannot be serialized must not leak the pooled connection it was going
 * to be sent on.
 */
class BodyWriterFailureReleasesConnectionSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            "spec.name": "BodyWriterFailureReleasesConnectionSpec",
            "micronaut.http.client.pool.enabled": true,
            "micronaut.http.client.pool.max-concurrent-http1-connections": 1,
            "micronaut.http.client.pool.acquire-timeout": "3s",
            // longer than the acquire timeout, so that a leaked connection is not closed by the
            // read timeout and silently replaced before the second request gives up
            "micronaut.http.client.read-timeout": "30s",
    ])

    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URI)

    def "connection is returned to the pool when the body writer throws"() {
        when: "a request whose body writer fails"
        client.toBlocking().exchange(HttpRequest.POST("/body-writer-failure/echo", new Unwritable()), String)

        then: "the client reports the writer error"
        def e = thrown(Exception)
        causeChain(e).any { it instanceof IllegalStateException && it.message == "cannot write body" }

        when: "a normal request is sent on the same client, which only has one connection"
        def response = client.toBlocking().retrieve("/body-writer-failure/ok")

        then: "the single connection was released and is reused"
        response == "ok"
    }

    private static List<Throwable> causeChain(Throwable t) {
        List<Throwable> chain = []
        while (t != null && !chain.contains(t)) {
            chain.add(t)
            t = t.cause
        }
        chain
    }

    static class Unwritable {
    }

    @Requires(property = "spec.name", value = "BodyWriterFailureReleasesConnectionSpec")
    @Singleton
    static class UnwritableWriter implements MessageBodyWriter<Unwritable> {
        @Override
        void writeTo(Argument<Unwritable> type,
                     MediaType mediaType,
                     Unwritable object,
                     MutableHeaders outgoingHeaders,
                     OutputStream outputStream) throws CodecException {
            throw new IllegalStateException("cannot write body")
        }
    }

    @Requires(property = "spec.name", value = "BodyWriterFailureReleasesConnectionSpec")
    @Controller("/body-writer-failure")
    static class TestController {

        @Post("/echo")
        String echo(@Body String body) {
            body
        }

        @Get("/ok")
        String ok() {
            "ok"
        }
    }
}
