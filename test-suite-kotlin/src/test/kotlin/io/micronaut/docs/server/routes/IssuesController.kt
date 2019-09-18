
package io.micronaut.docs.server.routes

// tag::imports[]
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
// end::imports[]


/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Controller("/issues") // <1>
class IssuesController {

    @Get("/{number}") // <2>
    internal fun issue(@PathVariable number: Int?): String { // <3>
        return "Issue # $number!" // <4>
    }
}
// end::class[]
