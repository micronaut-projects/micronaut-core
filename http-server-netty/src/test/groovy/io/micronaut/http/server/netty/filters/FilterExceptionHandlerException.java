package io.micronaut.http.server.netty.filters;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Singleton
public class FilterExceptionHandlerException implements ExceptionHandler<FilterExceptionException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, FilterExceptionException exception) {
        throw new RuntimeException("from exception handler");
    }
}
