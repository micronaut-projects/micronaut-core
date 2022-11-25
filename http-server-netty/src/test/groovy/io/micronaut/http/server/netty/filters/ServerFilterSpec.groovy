package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification

class ServerFilterSpec extends Specification {
    def 'simple filter'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ServerFilterSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()
        def filter = ctx.getBean(MyFilter)

        when:
        def response = client.exchange("/my-filter/index", String)
        then:
        response.body() == "foo"
        filter.events == ['request /my-filter/index', 'response /my-filter/index OK']

        cleanup:
        server.stop()
        ctx.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerFilterSpec")
    @Controller
    static class Ctrl {
        @Get("/my-filter/index")
        String myFilterIndex() {
            return "foo"
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerFilterSpec")
    @ServerFilter("/my-filter/**")
    static class MyFilter {
        def events = new ArrayList<String>()

        @RequestFilter
        void request(HttpRequest<?> request) {
            events.add("request " + request.uri)
        }

        @ResponseFilter
        void response(HttpRequest<?> request, HttpResponse<?> response) {
            events.add("response " + request.uri + " " + response.status)
        }
    }
}
