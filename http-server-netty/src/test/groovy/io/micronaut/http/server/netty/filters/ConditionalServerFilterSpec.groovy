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
import io.micronaut.http.filter.ConditionalFilter
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification

class ConditionalServerFilterSpec extends Specification {

    def 'conditional filter'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ConditionalServerFilterSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()
        def filter = ctx.getBean(MyFilter)

        when:
        def response = client.exchange("/my-filter/index", String)
        then:
        response.body() == "foo"
        filter.events == ['ConditionalFilter#isEnabled /my-filter/index', 'ConditionalFilter#isEnabled /my-filter/index', 'request /my-filter/index', 'response /my-filter/index OK']

        when:
        filter.events.clear()
        response = client.exchange("/other-filter/index", String)
        then:
        response.body() == "bar"
        filter.events == ['ConditionalFilter#isEnabled /other-filter/index', 'ConditionalFilter#isEnabled /other-filter/index']

        cleanup:
        server.stop()
        ctx.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "ConditionalServerFilterSpec")
    @ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
    static class MyFilter implements ConditionalFilter {
        def events = new ArrayList<String>()

        @RequestFilter
        void request(HttpRequest<?> request) {
            events.add("request " + request.uri)
        }

        @ResponseFilter
        void response(HttpRequest<?> request, HttpResponse<?> response) {
            events.add("response " + request.uri + " " + response.status)
        }

        @Override
        boolean isEnabled(HttpRequest<?> request) {
            events.add("ConditionalFilter#isEnabled " + request.uri)
            return request.getPath().startsWith("/my-filter")
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ConditionalServerFilterSpec")
    @Controller
    static class Ctrl {
        @Get("/my-filter/index")
        String myFilterIndex() {
            return "foo"
        }

        @Get("/other-filter/index")
        String otherFilterIndex() {
            return "bar"
        }

    }
}
