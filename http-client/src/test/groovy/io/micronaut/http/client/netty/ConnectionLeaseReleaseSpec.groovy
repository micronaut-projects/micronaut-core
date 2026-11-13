package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.net.URI

class ConnectionLeaseReleaseSpec extends Specification {

    def 'a request whose body cannot be encoded gives its connection back'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                                 : 'ConnectionLeaseReleaseSpec',
                'micronaut.http.client.pool.max-concurrent-http1-connections': 1,
                'micronaut.http.client.pool.acquire-timeout'                : '3s',
                'micronaut.http.client.read-timeout'                        : '60s',
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when: 'the body cannot be encoded while the connection is still being opened, so the request is built on the event loop'
        client.toBlocking().exchange(HttpRequest.POST('/lease/hello', new Unencodable()).contentType('application/x-lease-unknown'))
        then:
        thrown(RuntimeException)

        when: 'the only connection is open and idle'
        def first = client.toBlocking().retrieve('/lease/hello')
        then:
        first == 'hello'

        when: 'no writer encodes this body: the request fails after its connection was taken'
        client.toBlocking().exchange(HttpRequest.POST('/lease/hello', new Unencodable()).contentType('application/x-lease-unknown'))
        then:
        thrown(RuntimeException)

        when: 'the connection is free for the next request'
        def second = client.toBlocking().retrieve('/lease/hello')
        then:
        second == 'hello'

        cleanup:
        client.close()
        server.close()
    }

    def 'a request whose pipeline customizer fails gives up its connection and releases its body (#bodyKind)'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                                 : 'ConnectionLeaseReleaseSpec',
                'micronaut.http.client.pool.max-concurrent-http1-connections': 1,
                'micronaut.http.client.pool.acquire-timeout'                : '3s',
                'micronaut.http.client.read-timeout'                        : '60s',
        ])
        def failing = new FailingRequestPipeline()
        // passed to the client directly: customizers registered with the context are called
        // through a composite that logs and drops their exceptions
        HttpClient client = DefaultHttpClient.builder()
                .uri(server.URI)
                .configuration(server.applicationContext.getBean(HttpClientConfiguration))
                .clientCustomizer(failing)
                .build()
        String base = server.URL.toString()

        when: 'the request pipeline customizer fails once'
        failing.failNext = true
        client.toBlocking().exchange(request.uri(URI.create(base + request.path)))
        then:
        def e = thrown(RuntimeException)
        e.message.contains(FailingRequestPipeline.MESSAGE) || e.cause?.message?.contains(FailingRequestPipeline.MESSAGE)

        when: 'the only connection slot is free for the next request'
        def next = client.toBlocking().retrieve(base + '/lease/hello')
        then:
        next == 'hello'

        cleanup:
        client.close()
        server.close()

        where:
        bodyKind         | request
        'no body'        | HttpRequest.GET('/lease/hello')
        'full body'      | HttpRequest.POST('/lease/echo', 'some body').contentType('text/plain')
        'expect continue'| HttpRequest.POST('/lease/echo', 'some body').contentType('text/plain').header('Expect', '100-continue')
    }

    def 'a request whose headers cannot be encoded gives its connection back'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                                 : 'ConnectionLeaseReleaseSpec',
                'micronaut.http.client.pool.max-concurrent-http1-connections': 1,
                'micronaut.http.client.pool.acquire-timeout'                : '3s',
                'micronaut.http.client.read-timeout'                        : '60s',
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when: 'the only connection is open and idle'
        def first = client.toBlocking().retrieve('/lease/hello?q=1')
        then:
        first == 'hello'

        when: 'a header value with a line break is refused while the request is built, after its connection was taken'
        client.toBlocking().exchange(HttpRequest.GET('/lease/hello?q=2').header('X-Broken', 'a\r\nb'))
        then:
        thrown(RuntimeException)

        when: 'the connection is free for the next request'
        def second = client.toBlocking().retrieve('/lease/hello?q=3')
        then:
        second == 'hello'

        cleanup:
        client.close()
        server.close()
    }

    static class Unencodable {
    }

    static class FailingRequestPipeline implements NettyClientCustomizer {
        static final String MESSAGE = 'request pipeline customizer failed'

        volatile boolean failNext

        @Override
        void onRequestPipelineBuilt() {
            if (failNext) {
                failNext = false
                throw new IllegalStateException(MESSAGE)
            }
        }
    }

    @Controller('/lease')
    @Requires(property = 'spec.name', value = 'ConnectionLeaseReleaseSpec')
    static class LeaseController {
        @Get('/hello')
        String hello() {
            'hello'
        }

        @Post(value = '/echo', consumes = 'text/plain')
        String echo(@Body String body) {
            body
        }
    }
}
