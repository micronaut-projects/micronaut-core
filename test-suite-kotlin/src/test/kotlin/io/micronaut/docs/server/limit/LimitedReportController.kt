package io.micronaut.docs.server.limit

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.limit.RequestLimit
// end::imports[]

@Requires(property = "spec.name", value = "RequestLimitTest")
// tag::clazz[]
@Controller("/limited-reports")
class LimitedReportController {

    @RequestLimit("reports") // <1>
    @Get("/{id}")
    fun report(id: String) = "report $id"
}
// end::clazz[]
