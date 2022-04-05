package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.Specification

class FilterReplaceRequestSpec extends Specification {
    def 'test replaced http request is handled by next filter'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FilterReplaceRequestSpec'])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URI)
        Filter1 filter1 = server.applicationContext.getBean(Filter1)
        Filter2 filter2 = server.applicationContext.getBean(Filter2)

        when:
        HttpResponse<String> resp = client.toBlocking().exchange("/initial", String)
        then:
        resp.body() == "initial"
        filter1.filteredRequest.path == "/initial"
        filter2.filteredRequest.path == "/filter1"

        cleanup:
        server.stop()
        client.stop()
    }
    
    @Filter(Filter.MATCH_ALL_PATTERN)
    @Requires(property = 'spec.name', value = 'FilterReplaceRequestSpec')
    static class Filter1 implements HttpServerFilter {
        HttpRequest<?> filteredRequest = null

        @Override
        int getOrder() {
            1
        }

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            filteredRequest = request
            chain.proceed(HttpRequest.GET("/filter1"))
        }
    }

    @Filter(Filter.MATCH_ALL_PATTERN)
    @Requires(property = 'spec.name', value = 'FilterReplaceRequestSpec')
    static class Filter2 implements HttpServerFilter {
        HttpRequest<?> filteredRequest = null

        @Override
        int getOrder() {
            2
        }

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            filteredRequest = request
            chain.proceed(HttpRequest.GET("/filter2"))
        }
    }

    @Controller
    @Requires(property = 'spec.name', value = 'FilterReplaceRequestSpec')
    static class Ctrl {
        @Get("/filter2")
        String filter2() {
            "filter2"
        }

        @Get("/initial")
        String initial() {
            "initial"
        }
    }
}
