package io.micronaut.docs.server.intro

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Requires(property = "spec.name", value = "HelloControllerSpec")
@Controller("/hello")
class HelloController {
    @Get(produces = [MediaType.TEXT_PLAIN])
    fun index() = "Hello World"
}
