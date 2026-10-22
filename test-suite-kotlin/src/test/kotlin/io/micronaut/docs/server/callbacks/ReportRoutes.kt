package io.micronaut.docs.server.callbacks

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.ServerHttpRequest
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "ReportRoutesTest")
// tag::clazz[]
@Singleton
class ReportRoutes(private val reports: Reports) : HttpRoutes {
    override fun routes(routes: HttpRouteBuilder) {
        routes.GET("/reports/{id}").handleAsync { request, pathVariables ->
            val id = pathVariables.getString("id")
            val report = reports.generate(id)
            if (request is ServerHttpRequest<*>) {
                request.whenAbandoned { report.cancel(true) } // <1>
                request.whenResponseCompleted { failure -> reports.completed(id, failure) } // <2>
            }
            report.thenApply { text -> HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE) }
        }
    }
}
// end::clazz[]
