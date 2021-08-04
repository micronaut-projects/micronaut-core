package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.filter.FilterChain;
import io.micronaut.http.filter.HttpFilter;
import org.reactivestreams.Publisher;

import java.util.Objects;

@Filter("/chat/**")
@Requires(property = "websocket-filter-respond")
public class WebSocketNewResponseFilter implements HttpFilter {

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
        return Publishers.just(HttpResponse.ok("from-filter"));
    }

}
