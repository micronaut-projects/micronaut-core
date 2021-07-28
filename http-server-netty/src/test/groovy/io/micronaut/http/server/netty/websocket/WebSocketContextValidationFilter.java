package io.micronaut.http.server.netty.websocket;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.filter.FilterChain;
import io.micronaut.http.filter.HttpFilter;
import java.util.Objects;
import org.reactivestreams.Publisher;

@Filter("/context/chat/**")
public class WebSocketContextValidationFilter implements HttpFilter {

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
        if (!request.getAttribute(MARKER).isPresent()) {
            request.setAttribute(MARKER, true);
            HttpRequest<Object> currentRequest = ServerRequestContext.currentRequest().orElse(null);
            if (!Objects.equals(currentRequest, request)) {
                return Publishers.just(new IllegalStateException("Current request is not set properly: " + currentRequest));
            }
        }
        return chain.proceed(request);
    }

    private static final String MARKER = WebSocketContextValidationFilter.class.getName();
}
