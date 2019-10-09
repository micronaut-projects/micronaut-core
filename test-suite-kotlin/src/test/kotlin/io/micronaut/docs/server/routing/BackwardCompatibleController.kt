package io.micronaut.docs.server.routing

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

// tag::imports[]

// end::imports[]
@Requires(property = "spec.name", value = "BackwardCompatibleControllerSpec")
// tag::class[]
@Controller("/hello")
class BackwardCompatibleController {

    @Get(uris = ["/{name}", "/person/{name}"]) // <1>
    fun hello(name: String): String { // <2>
        return "Hello, $name"
    }
}
// end::class[]
