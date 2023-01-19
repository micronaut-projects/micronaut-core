package io.micronaut.docs.http.server.cors

import io.micronaut.context.annotation.Requires

// tag::imports[]
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.cors.CrossOrigin
// end::imports[]

@Requires(property = "spec.name", value = "CorsControllerSpec")
// tag::controller[]
@Controller("/hello")
class CorsController {
    @CrossOrigin("https://myui.com") // <1>
    @Get(produces = MediaType.TEXT_PLAIN) // <2>
    String cors() {
        return "Welcome to the worlds of CORS"
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/nocors") // <3>
    String nocorstoday() {
        return "No more CORS for you"
    }
}
// end::controller[]
