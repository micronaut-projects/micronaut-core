package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * An exchange leaves the request object of the caller as it was: reused for a stream, the
 * request streams the response body, which may never end.
 */
class JdkExchangeRequestReuseSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'JdkExchangeRequestReuseSpec'])

    void "a request reused after an exchange streams an endless body"() {
        given:
        StreamingHttpClient client = server.applicationContext.createBean(StreamingHttpClient, server.URL)
        MutableHttpRequest<?> request = HttpRequest.GET('/reuse/ticks')

        when: 'the request is used for an exchange, which buffers the body of the response'
        Mono.from(client.exchange(request.uri(URI.create('/reuse/once')), String)).block()

        then: 'the request of the caller is not marked to buffer the response'
        !request.getAttribute('micronaut.http.client.buffer-response').isPresent()

        when: 'the same request streams an endless body'
        ByteBuffer<?> first = Flux.from(client.dataStream(request.uri(URI.create('/reuse/ticks'))))
            .next()
            .block(Duration.ofSeconds(10))

        then: 'its first piece arrives'
        first.toString(StandardCharsets.UTF_8).startsWith('tick')

        cleanup:
        client.close()
    }

    @Requires(property = 'spec.name', value = 'JdkExchangeRequestReuseSpec')
    @Controller('/reuse')
    static class ReuseController {
        @Get(uri = '/once', produces = MediaType.TEXT_PLAIN)
        String once() {
            return 'once'
        }

        @Get(uri = '/ticks', produces = MediaType.TEXT_PLAIN)
        Flux<String> ticks() {
            return Flux.interval(Duration.ofMillis(50)).map { 'tick' + it }
        }
    }
}
