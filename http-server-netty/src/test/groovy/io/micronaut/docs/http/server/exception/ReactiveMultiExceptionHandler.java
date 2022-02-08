package io.micronaut.docs.http.server.exception;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

@Singleton
public class ReactiveMultiExceptionHandler implements ExceptionHandler<ReactiveMultiException, Publisher<HttpResponse<Publisher<String>>>> {

    @Override
    public Publisher<HttpResponse<Publisher<String>>> handle(HttpRequest request, ReactiveMultiException exception) {
        return Publishers.just(HttpResponse.ok(Flux.just("foo", "bar")));
    }
}
