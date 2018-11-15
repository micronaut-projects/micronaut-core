package io.micronaut.docs.server.intro

import io.micronaut.context.annotation.Requires

// tag::imports[]
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
// end::imports[]

@Requires(property = "spec.name", value = "HelloControllerSpec")
// tag::class[]
@Controller('/hello') // <1>
class HelloController {
    @Get(produces = MediaType.TEXT_PLAIN) // <2>
    String index() {
        'Hello World' // <3>
    }
}
// end::class[]
