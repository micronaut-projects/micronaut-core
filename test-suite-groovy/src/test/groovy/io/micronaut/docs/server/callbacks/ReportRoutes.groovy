package io.micronaut.docs.server.callbacks

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.ServerHttpRequest
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
// end::imports[]

@Requires(property = "spec.name", value = "ReportRoutesSpec")
// tag::clazz[]
@Singleton
class ReportRoutes implements HttpRoutes {
    private final Reports reports

    ReportRoutes(Reports reports) {
        this.reports = reports
    }

    @Override
    void routes(HttpRouteBuilder routes) {
        routes.GET("/reports/{id}").handleAsync { request, pathVariables ->
            String id = pathVariables.getString("id")
            CompletableFuture<String> report = reports.generate(id)
            if (request instanceof ServerHttpRequest) {
                ServerHttpRequest<?> serverRequest = (ServerHttpRequest<?>) request
                serverRequest.whenAbandoned { report.cancel(true) } // <1>
                serverRequest.whenResponseCompleted { Throwable failure -> reports.completed(id, failure) } // <2>
            }
            report.thenApply { String text -> HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE) }
        }
    }
}
// end::clazz[]
