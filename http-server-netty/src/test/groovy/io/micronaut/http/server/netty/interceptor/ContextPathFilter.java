package io.micronaut.http.server.netty.interceptor;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;

/**
 * Tests filters with the context path already prepended still work
 */
@Filter("/context/path/**")
public class ContextPathFilter implements HttpServerFilter {

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Publishers.then(chain.proceed(request), (response) -> {
            response.header("X-Context-Path", "true");
        });
    }

}
