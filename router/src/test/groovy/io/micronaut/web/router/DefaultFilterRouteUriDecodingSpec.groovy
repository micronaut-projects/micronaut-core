package io.micronaut.web.router

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.FilterOrder
import io.micronaut.http.filter.GenericHttpFilter
import io.micronaut.http.filter.HttpFilter
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.util.function.Supplier

/**
 * The URI and String overloads of filter matching decode the path exactly once.
 */
class DefaultFilterRouteUriDecodingSpec extends Specification {

    void "the URI overload matches like the String overload of the raw path"(String target, boolean expected) {
        given:
        def filter = GenericHttpFilter.createLegacyFilter(new HttpFilter() {
            @Override
            Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
                return null
            }
        }, new FilterOrder.Fixed(0))
        def route = new DefaultFilterRoute("/priv/**", { filter } as Supplier<GenericHttpFilter>)

        expect:
        route.match(HttpMethod.GET, target).isPresent() == expected
        route.match(HttpMethod.GET, URI.create(target)).isPresent() == expected

        where:
        target                | expected
        '/priv/data.txt'      | true
        '/priv%2Fdata.txt'    | true
        '/priv%252Fdata.txt'  | false
    }
}
