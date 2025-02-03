package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.http.BasicHttpAttributes
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.FilterContinuation
import io.micronaut.http.server.annotation.PreMatching
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.web.router.RouteAttributes
import jakarta.inject.Singleton
import spock.lang.Specification

class ServerPreFilterSpec extends Specification {
    def 'simple filter'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ServerPreFilterSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()
        def filter = ctx.getBean(MyFilter)

        when:
        def response = client.exchange("/my-filter/index", String)
        then:
        response.body() == "foo"
        filter.events == ['prematching /my-filter/index', 'request /my-filter/index', 'response /my-filter/index OK']

        cleanup:
        server.stop()
        ctx.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerPreFilterSpec")
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

        @PreMatching
        @RequestFilter
        void preMatchingRequest(HttpRequest<?> request) {
            if (RouteAttributes.getRouteInfo(request).isPresent()) {
                throw new IllegalStateException()
            }
            if (RouteAttributes.getRouteMatch(request).isPresent()) {
                throw new IllegalStateException()
            }
            if (BasicHttpAttributes.getUriTemplate(request).isPresent()) {
                throw new IllegalStateException()
            }
            events.add("prematching " + request.uri)
        }

    }

    def 'filter order'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ServerPreFilterSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()
        def filter = ctx.getBean(OrderFilter)

        when:
        def response = client.exchange("/order-filter/index", String)
        then:
        response.body() == "foo"
        filter.events == ['pre match 01', 'pre match 02', 'pre match 03', 'request 1', 'request 2', 'request 3', 'response 3', 'response 2', 'response 1']

        cleanup:
        server.stop()
        ctx.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerPreFilterSpec")
    @ServerFilter("/order-filter/**")
    static class OrderFilter {
        def events = new ArrayList<String>()

        @Order(1)
        @PreMatching
        @RequestFilter
        void preMatchingRequest1(HttpRequest<?> request) {
            validateRoutes(request)

            events.add("pre match 01")
        }

        @Order(3)
        @PreMatching
        @RequestFilter
        void preMatchingRequest3(HttpRequest<?> request) {
            validateRoutes(request)
            events.add("pre match 03")
        }

        @Order(2)
        @PreMatching
        @RequestFilter
        void preMatchingRequest2(HttpRequest<?> request) {
            validateRoutes(request)
            events.add("pre match 02")
        }

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

        private static void validateRoutes(HttpRequest<?> request) {
            if (RouteAttributes.getRouteInfo(request).isPresent()) {
                throw new IllegalStateException()
            }
            if (RouteAttributes.getRouteMatch(request).isPresent()) {
                throw new IllegalStateException()
            }
            if (BasicHttpAttributes.getUriTemplate(request).isPresent()) {
                throw new IllegalStateException()
            }
        }
    }

    def 'blocking filter'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ServerPreFilterSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()
        def filter = ctx.getBean(BlockingFilter)

        when:
        def response = client.exchange("/blocking-filter/index", String)
        then:
        response.body() == "foo"
        filter.events == ['prematching /blocking-filter/index', 'request /blocking-filter/index', 'response /blocking-filter/index OK']

        cleanup:
        server.stop()
        ctx.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerPreFilterSpec")
    @ServerFilter("/blocking-filter/**")
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class BlockingFilter {
        def events = new ArrayList<String>()

        @PreMatching
        @RequestFilter
        void preMatchingRequest(HttpRequest<?> request) {
            if (RouteAttributes.getRouteInfo(request).isPresent()) {
                throw new IllegalStateException()
            }
            if (RouteAttributes.getRouteMatch(request).isPresent()) {
                throw new IllegalStateException()
            }
            if (BasicHttpAttributes.getUriTemplate(request).isPresent()) {
                throw new IllegalStateException()
            }
            events.add("prematching " + request.uri)
        }

        @RequestFilter
        void request(HttpRequest<?> request, FilterContinuation<HttpResponse<?>> continuation) {
            events.add("request " + request.uri)
            def response = continuation.proceed()
            events.add("response " + request.uri + " " + response.status)
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerPreFilterSpec")
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

    def 'pre matching filter changing path'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ServerPreFilterSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def response = client.retrieve("/changing/$path", String)
        then:
        response == result

        cleanup:
        server.stop()
        ctx.close()

        where:
        path | result
        "abc" | "abc"
        "foo" | "foo"
        "xyz" | "abc"
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerPreFilterSpec")
    @ServerFilter("/changing/**")
    static class ChangingFilter {

        @Order(1)
        @PreMatching
        @RequestFilter
        MutableHttpRequest<?> preMatchingRequest1(MutableHttpRequest<?> request) {
            return request.uri(new URI(request.getUri().toString().replace("xyz", "HELLO")))
        }

        @Order(2)
        @PreMatching
        @RequestFilter
        void preMatchingRequest2(MutableHttpRequest<?> request) {
            request.uri(new URI(request.getUri().toString().replace("HELLO", "abc")))
        }

    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerPreFilterSpec")
    @Controller
    static class ChangingFilterController {
        @Get("/changing/xyz")
        String xyz() {
            return "xyz"
        }

        @Get("/changing/foo")
        String foo() {
            return "foo"
        }

        @Get("/changing/abc")
        String abc() {
            return "abc"
        }
    }

    def 'blocking pre-matching filter'() {
        given:
            def ctx = ApplicationContext.run(['spec.name': 'ServerPreFilterSpec'])
            def server = ctx.getBean(EmbeddedServer)
            server.start()
            def client = ctx.createBean(HttpClient, server.URI).toBlocking()
            def filter = ctx.getBean(BlockingPreMatchingFilter)

        when:
            def response = client.exchange("/blocking-pre-matching-filter/index", String)
        then:
            response.body() == "foo"
            filter.events == ['prematching /blocking-pre-matching-filter/index', 'request /blocking-pre-matching-filter/index', 'response /blocking-pre-matching-filter/index OK', 'after prematching /blocking-pre-matching-filter/index']

        cleanup:
            server.stop()
            ctx.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerPreFilterSpec")
    @ServerFilter("/blocking-pre-matching-filter/**")
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class BlockingPreMatchingFilter {
        def events = new ArrayList<String>()

        @PreMatching
        @RequestFilter
        void preMatchingRequest(HttpRequest<?> request, FilterContinuation<HttpResponse<?>> continuation) {
            if (RouteAttributes.getRouteInfo(request).isPresent()) {
                throw new IllegalStateException()
            }
            if (RouteAttributes.getRouteMatch(request).isPresent()) {
                throw new IllegalStateException()
            }
            if (BasicHttpAttributes.getUriTemplate(request).isPresent()) {
                throw new IllegalStateException()
            }
            events.add("prematching " + request.uri)
            def response = continuation.proceed()
            events.add("after prematching " + request.uri)
            if (RouteAttributes.getRouteInfo(request).isEmpty()) {
                throw new IllegalStateException()
            }
            if (RouteAttributes.getRouteMatch(request).isEmpty()) {
                throw new IllegalStateException()
            }
            if (BasicHttpAttributes.getUriTemplate(request).isEmpty()) {
                throw new IllegalStateException()
            }
        }

        @RequestFilter
        void request(HttpRequest<?> request, FilterContinuation<HttpResponse<?>> continuation) {
            events.add("request " + request.uri)
            def response = continuation.proceed()
            events.add("response " + request.uri + " " + response.status)
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServerPreFilterSpec")
    @Controller
    static class BlockingPreMatchingCtrl {

        @Get("/blocking-pre-matching-filter/index")
        String blockingFilterIndex() {
            return "foo"
        }
    }
}
