package io.micronaut.docs.server.limit

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.limit.RequestLimit
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "RequestLimitTest")
// tag::clazz[]
@Singleton
class LimitedReportRoutes : HttpRoutes {

    override fun routes(routes: HttpRouteBuilder) {
        routes.GET("/limited-reports/functional/{id}")
            .attribute(RequestLimit.ATTRIBUTE, "reports") // <1>
            .handle { _, pathVariables -> HttpResponse.ok("report " + pathVariables.getString("id")) }
    }
}
// end::clazz[]
