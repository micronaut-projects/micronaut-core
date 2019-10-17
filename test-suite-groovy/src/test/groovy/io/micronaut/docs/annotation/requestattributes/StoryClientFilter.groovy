package io.micronaut.docs.annotation.requestattributes

import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import org.reactivestreams.Publisher

@Filter("/story/**")
class StoryClientFilter implements HttpClientFilter {

    private Map<String, Object> attributes

    @Override
    Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
        attributes = request.getAttributes().asMap()
        return chain.proceed(request)
    }

    /**
     * strictly for unit testing
     */
    Map<String, Object> getLatestRequestAttributes() {
        return new HashMap<>(attributes)
    }
}
