package io.micronaut.docs.server.limit

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.limit.RequestLimit
// end::imports[]

@Requires(property = "spec.name", value = "RequestLimitSpec")
// tag::clazz[]
@Controller("/limited-reports")
class LimitedReportController {

    @RequestLimit("reports") // <1>
    @Get("/{id}")
    String report(String id) {
        "report $id"
    }
}
// end::clazz[]
