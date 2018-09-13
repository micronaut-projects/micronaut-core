package io.micronaut.security.overrideatsecured

import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.Secured

@Requires(env = Environment.TEST)
@Requires(property = 'spec.name', value = 'OverrideAtSecuredControllerSpec')
@Controller(OverrideAtSecuredControllerSpec.controllerPath)
class BookController {

    @Secured("ROLE_ADMIN")
    @Get("/books")
    Map<String, Object> list() {
        [books: ['Building Microservice', 'Release it']]
    }
}
