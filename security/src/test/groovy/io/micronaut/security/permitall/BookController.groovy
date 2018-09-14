package io.micronaut.security.permitall

import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import javax.annotation.security.PermitAll

@Requires(env = Environment.TEST)
@Requires(property = 'spec.name', value = 'PermitAllSpec')
@Controller(PermitAllSpec.controllerPath)
class BookController {

    @PermitAll
    @Get("/books")
    Map<String, Object> list() {
        [books: ['Building Microservice', 'Release it']]
    }
}
