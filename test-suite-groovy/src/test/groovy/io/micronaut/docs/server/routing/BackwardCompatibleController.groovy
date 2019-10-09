package io.micronaut.docs.server.routing;

import io.micronaut.context.annotation.Requires

// tag::imports[]
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
// end::imports[]
@Requires(property = "spec.name", value = "BackwardCompatibleControllerSpec")
// tag::class[]
@Controller("/hello")
class BackwardCompatibleController {

    @Get(uris = ["/{name}", "/person/{name}"]) // <1>
    String hello(String name) { // <2>
        "Hello, " + name
    }
}
// end::class[]
