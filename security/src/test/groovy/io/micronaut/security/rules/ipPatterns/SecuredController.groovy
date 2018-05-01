package io.micronaut.security.rules.ipPatterns

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule

@Requires(property = 'spec.name', value = 'ipPatterns')
@Controller("/secured")
@Secured(SecurityRule.IS_AUTHENTICATED)
class SecuredController {

    @Get("/authenticated")
    String authenticated(Authentication authentication) {
        "${authentication.getName()} is authenticated"
    }
}
