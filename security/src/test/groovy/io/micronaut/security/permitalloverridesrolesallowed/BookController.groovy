package io.micronaut.security.permitalloverridesrolesallowed

import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

import javax.annotation.security.PermitAll
import javax.annotation.security.RolesAllowed

@Requires(env = Environment.TEST)
@Requires(property = 'spec.name', value = 'PermitAllOverridesRolesAllowedSpec')
@Controller(PermitAllOverridesRolesAllowedSpec.controllerPath)
@RolesAllowed(['ROLE_ADMIN'])
class BookController {

    @PermitAll
    @Get("/books")
    Map<String, Object> list() {
        [books: ['Building Microservice', 'Release it']]
    }
}
