package io.micronaut.docs.http.server.exception;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

@Singleton
public class ReactiveExceptionHandler implements ExceptionHandler<ReactiveException, Publisher<HttpResponse<Publisher<String>>>> {

    @Override
    public Publisher<HttpResponse<Publisher<String>>> handle(HttpRequest request, ReactiveException exception) {
        return Publishers.just(HttpResponse.ok(Publishers.just("reactive handler")));
    }
}
