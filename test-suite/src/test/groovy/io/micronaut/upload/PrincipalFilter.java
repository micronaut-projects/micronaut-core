package io.micronaut.upload;

import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;

import java.security.Principal;

@Filter("/upload/receive-multipart-body-principal")
public class PrincipalFilter implements HttpServerFilter {

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        request.setAttribute(HttpAttributes.PRINCIPAL, (Principal) () -> "test");
        return chain.proceed(request);
    }
}
