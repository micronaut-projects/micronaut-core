package io.micronaut.docs.server.intro

import io.micronaut.context.annotation.Requires

// tag::imports[]
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
// end::imports[]

@Requires(property = "spec.name", value = "HelloControllerSpec")
// tag::class[]
@Controller("/hello") // <1>
class HelloController {

    @Produces(MediaType.TEXT_PLAIN)
    @Get // <2>
    fun index(): String {
        return "Hello World" // <3>
    }
}
// end::class[]
