package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.FilterContinuation
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
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

    def 'filter order'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ServerFilterSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()
        def filter = ctx.getBean(OrderFilter)

        when:
        def response = client.exchange("/order-filter/index", String)
        then:
        response.body() == "foo"
        filter.events == ['request 1', 'request 2', 'request 3', 'response 3', 'response 2', 'response 1']

        cleanup:
        server.stop()
        ctx.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerFilterSpec")
    @ServerFilter("/order-filter/**")
    static class OrderFilter {
        def events = new ArrayList<String>()

        @RequestFilter
        @Order(1)
        void requestA(HttpRequest<?> request) {
            events.add("request 1")
        }

        @RequestFilter
        @Order(3)
        void requestB(HttpRequest<?> request) {
            events.add("request 3")
        }

        @RequestFilter
        @Order(2)
        void requestC(HttpRequest<?> request) {
            events.add("request 2")
        }

        @ResponseFilter
        @Order(1)
        void responseA(HttpRequest<?> request, HttpResponse<?> response) {
            events.add("response 1")
        }

        @ResponseFilter
        @Order(3)
        void responseB(HttpRequest<?> request, HttpResponse<?> response) {
            events.add("response 3")
        }

        @ResponseFilter
        @Order(2)
        void responseC(HttpRequest<?> request, HttpResponse<?> response) {
            events.add("response 2")
        }
    }

    def 'blocking filter'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ServerFilterSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()
        def filter = ctx.getBean(BlockingFilter)

        when:
        def response = client.exchange("/blocking-filter/index", String)
        then:
        response.body() == "foo"
        filter.events == ['request /blocking-filter/index', 'response /blocking-filter/index OK']

        cleanup:
        server.stop()
        ctx.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerFilterSpec")
    @ServerFilter("/blocking-filter/**")
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class BlockingFilter {
        def events = new ArrayList<String>()

        @RequestFilter
        void request(HttpRequest<?> request, FilterContinuation<HttpResponse<?>> continuation) {
            events.add("request " + request.uri)
            def response = continuation.proceed()
            events.add("response " + request.uri + " " + response.status)
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerFilterSpec")
    @Controller
    static class Ctrl {
        @Get("/my-filter/index")
        String myFilterIndex() {
            return "foo"
        }

        @Get("/order-filter/index")
        String orderFilterIndex() {
            return "foo"
        }

        @Get("/blocking-filter/index")
        String blockingFilterIndex() {
            return "foo"
        }
    }
}
