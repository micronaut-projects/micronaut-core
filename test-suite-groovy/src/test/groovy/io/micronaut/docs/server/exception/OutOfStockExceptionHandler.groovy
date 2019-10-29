package io.micronaut.docs.server.exception;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;

import javax.inject.Singleton;

@Requires(property = "spec.name", value = "ExceptionHandlerSpec")
//tag::clazz[]
@Produces
@Singleton
@Requires(classes = [OutOfStockException.class, ExceptionHandler.class])
class OutOfStockExceptionHandler implements ExceptionHandler<OutOfStockException, HttpResponse> {

    @Override
    HttpResponse handle(HttpRequest request, OutOfStockException exception) {
        HttpResponse.ok(0)
    }
}
//end::clazz[]
