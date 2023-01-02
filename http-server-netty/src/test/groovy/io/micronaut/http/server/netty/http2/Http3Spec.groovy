package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

class Http3Spec extends Specification {
    def 'simple request'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.server.netty.listeners.a.family': 'QUIC',

                "micronaut.http.client.alpn-modes" : ["h3"],
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI)

        when:
        client.toBlocking().exchange("/")
        then:
        def e = thrown HttpClientResponseException
        e.status == HttpStatus.NOT_FOUND

        when:
        client.toBlocking().exchange("/")
        then:
        e = thrown HttpClientResponseException
        e.status == HttpStatus.NOT_FOUND

        cleanup:
        server.close()
        client.close()
        ctx.close()
    }

    def 'streaming response'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'Http3Spec',

                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.server.netty.listeners.a.family': 'QUIC',
                'micronaut.server.netty.listeners.a.port': -1,

                "micronaut.http.client.alpn-modes" : ["h3"],
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI)

        when:
        def resp = client.toBlocking().exchange("/h3/stream", String)
        then:
        resp.body() == '["foo","bar"]'
    }

    @Controller
    @Requires(property = "spec.name", value = "Http3Spec")
    static class Ctrl {
        @Get('/h3/stream')
        Publisher<String> stream() {
            return Flux.fromIterable(['"foo"', '"bar"'])
        }
    }
}
