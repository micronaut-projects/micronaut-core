package io.micronaut.http.server;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.reactivestreams.Publisher;

@FunctionalInterface
public interface MicronautAsyncHttpHandler {
    Publisher<? extends HttpResponse<?>> handleAsync(HttpRequest<
        ?> request);
}
