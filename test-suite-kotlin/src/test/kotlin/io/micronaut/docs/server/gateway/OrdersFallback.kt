package io.micronaut.docs.server.gateway

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.PathVariables
import io.micronaut.http.gateway.GatewayRequestContext
import io.micronaut.web.router.builder.RequestHandler
import jakarta.inject.Named
import jakarta.inject.Singleton

@Requires(property = "spec.name", value = "GatewayDocsTest")
// tag::clazz[]
@Singleton
@Named("ordersFallback")
class OrdersFallback : RequestHandler {

    override fun handle(request: HttpRequest<*>, pathVariables: PathVariables): HttpResponse<*> {
        val reason = GatewayRequestContext.find(request)
            .flatMap { it.failure() }
            .map { it.javaClass.simpleName }
            .orElse("none")
        return HttpResponse.status<Any>(HttpStatus.OK).body("fallback: $reason")
    }
}
// end::clazz[]
