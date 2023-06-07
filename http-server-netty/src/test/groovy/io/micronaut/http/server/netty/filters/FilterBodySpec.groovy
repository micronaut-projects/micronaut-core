package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import spock.lang.Specification

class FilterBodySpec extends Specification {
    def "delayed body"() {
        given:
        def ctx = ApplicationContext.run(["spec.name":"FilterBodySpec"])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI)
        def filter = ctx.getBean(MyFilter)

        when:
        filter.secondBodyPartSink = Sinks.one()
        def resp = client.toBlocking().exchange(HttpRequest.POST("/foo", Flux.concat(
                Flux.just("foo"),
                filter.secondBodyPartSink.asMono()
        )).contentType(MediaType.TEXT_PLAIN_TYPE), String)
        then:
        resp.body() == "foobar"

        cleanup:
        client.close()
        server.stop()
        ctx.close()
    }

    @Requires(property = 'spec.name', value = 'FilterBodySpec')
    @ServerFilter("/foo")
    @Singleton
    static class MyFilter {
        Sinks.One<String> secondBodyPartSink

        @RequestFilter
        @Order(-1)
        void signal() {
            secondBodyPartSink.emitValue("bar", Sinks.EmitFailureHandler.FAIL_FAST)
        }

        @RequestFilter
        @Order(0)
        HttpResponse<String> await(@Body String body) {
            return HttpResponse.ok(body)
        }
    }
}
