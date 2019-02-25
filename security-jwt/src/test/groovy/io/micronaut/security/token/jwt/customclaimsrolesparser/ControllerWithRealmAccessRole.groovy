package io.micronaut.security.token.jwt.customclaimsrolesparser

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Status
import io.micronaut.security.annotation.Secured

@CompileStatic
@Requires(property = "spec.name", value = "customclaimsrolesparser")
@Controller("/")
class ControllerWithRealmAccessRole {

    @Secured("uma_authorization")
    @Get("/")
    @Status(HttpStatus.OK)
    void index() {}
}
