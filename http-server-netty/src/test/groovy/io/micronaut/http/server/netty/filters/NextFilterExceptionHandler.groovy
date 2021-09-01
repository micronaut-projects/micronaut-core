package io.micronaut.http.server.netty.filters

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.exceptions.ExceptionHandler

import jakarta.inject.Singleton

@Singleton
class NextFilterExceptionHandler implements ExceptionHandler<NextFilterException, HttpResponse<?>> {

    @Override
    HttpResponse<?> handle(HttpRequest request, NextFilterException exception) {
        return HttpResponse.badRequest("from NEXT filter exception handler")
    }
}
