package io.micronaut.docs.server.exception

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler

import javax.inject.Singleton

//tag::clazz[]
@Produces
@Singleton
@Requirements(
//end::clazz[]
        Requires(property = "spec.name", value = "ExceptionHandlerSpec"),
//tag::clazz[]
        Requires(classes = [OutOfStockException::class, ExceptionHandler::class])
)
class OutOfStockExceptionHandler : ExceptionHandler<OutOfStockException, HttpResponse<*>> {

    override fun handle(request: HttpRequest<*>, exception: OutOfStockException): HttpResponse<*> {
        return HttpResponse.ok(0)
    }
}
//end::clazz[]
