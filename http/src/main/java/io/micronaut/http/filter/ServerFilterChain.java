package io.micronaut.http.filter;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;

/**
 * <p>A non-blocking and thread-safe filter chain. Consumers should call {@link #proceed(HttpRequest)} to continue with the request or return an alternative {@link HttpResponse} {@link Publisher}</p>
 *
 * <p>The context instance itself can be passed to other threads as necessary if blocking operations are required to implement the {@link HttpFilter}</p>
 */
public interface ServerFilterChain extends FilterChain {
    /**
     * Proceed to the next interceptor or final request invocation
     *
     * @param request The current request
     */
    Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> request);
}
