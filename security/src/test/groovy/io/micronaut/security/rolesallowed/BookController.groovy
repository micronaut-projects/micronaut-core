package io.micronaut.security.rolesallowed

import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

import javax.annotation.security.RolesAllowed

@Requires(env = Environment.TEST)
@Requires(property = 'spec.name', value = 'RolesAllowedSpec')
@RolesAllowed(['ROLE_USER'])
@Controller(RolesAllowedSpec.controllerPath)
class BookController {

    @RolesAllowed(['ROLE_USER', 'ROLE_ADMIN'])
    @Get("/books")
    Map<String, Object> list() {
        [books: ['Building Microservice', 'Release it']]
    }

    @Get("/classlevel")
    Map<String, Object> classlevel() {
        [books: ['Building Microservice', 'Release it']]
    }

    @RolesAllowed(['ROLE_ADMIN', 'ROLE_MANAGER'])
    @Get("/forbidenbooks")
    Map<String, Object> forbiddenList() {
        [books: ['Building Microservice', 'Release it']]
    }
}
