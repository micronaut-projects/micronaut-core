package io.micronaut.http.server.netty.filters

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.exceptions.ExceptionHandler

import javax.inject.Singleton

@Singleton
class FilterExceptionHandler implements ExceptionHandler<FilterException, HttpResponse<?>> {

    @Override
    HttpResponse<?> handle(HttpRequest request, FilterException exception) {
        return HttpResponse.badRequest("from filter exception handler")
    }
}
