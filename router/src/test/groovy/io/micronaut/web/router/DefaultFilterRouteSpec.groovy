package io.micronaut.web.router

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.HttpFilter
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.util.function.Supplier

class DefaultFilterRouteSpec extends Specification {

    void "test filter route matching with no methods specified"() {
        given:
        def filter = new HttpFilter() {
            @Override
            Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
                return null
            }
        }

        when:
        def route = new DefaultFilterRoute("/foo", new Supplier<HttpFilter>() {
            @Override
            HttpFilter get() {
                return filter
            }
        })

        then: //all methods match
        route.match(HttpMethod.GET, URI.create('/foo')).isPresent()
        route.match(HttpMethod.POST, URI.create('/foo')).isPresent()
        route.match(HttpMethod.PUT, URI.create('/foo')).isPresent()
    }

    void "test filter route matching with methods specified"() {
        given:
        def filter = new HttpFilter() {
            @Override
            Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
                return null
            }
        }

        when:
        def route = new DefaultFilterRoute("/foo", new Supplier<HttpFilter>() {
            @Override
            HttpFilter get() {
                return filter
            }
        }).methods(HttpMethod.POST, HttpMethod.PUT)

        then: //get does not match
        !route.match(HttpMethod.GET, URI.create('/foo')).isPresent()
        route.match(HttpMethod.POST, URI.create('/foo')).isPresent()
        route.match(HttpMethod.PUT, URI.create('/foo')).isPresent()
    }
}
