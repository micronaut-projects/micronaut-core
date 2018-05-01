package io.micronaut.security.authorization

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.rules.SecurityRule

@Requires(property = 'spec.name', value = 'authorization')
@Controller("/secured")
@Secured(SecurityRule.IS_AUTHENTICATED)
class SecuredController {

    @Get("/admin")
    @Secured(["ROLE_ADMIN", "ROLE_X"])
    String admin() {
        "You have admin"
    }

    @Get("/authenticated")
    String authenticated(Authentication authentication) {
        "${authentication.getName()} is authenticated"
    }
}
