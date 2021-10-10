package io.micronaut.docs.server.routes;

// tag::imports[]

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
// end::imports[]

// tag::class[]
@Controller("/issues") // <1>
class IssuesController {

    @Get("/{number}") // <2>
    String issue(@PathVariable Integer number) { // <3>
        "Issue # " + number + "!" // <4>
    }
}
// end::class[]
