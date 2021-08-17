package io.micronaut.http.server.netty.websocket;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.filter.FilterChain;
import io.micronaut.http.filter.HttpFilter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Publisher;

@Filter({"/chat/**", "/abc/**"})
public class WebSocketContextValidationFilter implements HttpFilter {

    AtomicInteger executeCount = new AtomicInteger();

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
        executeCount.incrementAndGet();
        HttpRequest<Object> currentRequest = ServerRequestContext.currentRequest().orElse(null);
        if (!Objects.equals(currentRequest, request)) {
            return Publishers.just(new IllegalStateException("Current request is not set properly: " + currentRequest));
        }
        return chain.proceed(request);
    }
}
