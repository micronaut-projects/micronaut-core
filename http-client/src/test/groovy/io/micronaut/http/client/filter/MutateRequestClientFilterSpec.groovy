package io.micronaut.http.client.filter

import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import org.reactivestreams.Publisher
import spock.lang.Specification

class MutateRequestClientFilterSpec extends Specification {




    @Filter('/filters/test')
    static class MyFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            return null
        }
    }
}
