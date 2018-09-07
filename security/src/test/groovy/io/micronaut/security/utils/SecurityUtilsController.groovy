package io.micronaut.security.utils

import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.security.Secured
import javax.annotation.Nullable

@Requires(env = Environment.TEST)
@Requires(property = SecurityUtilsSpec.SPEC_NAME_PROPERTY, value = 'SecurityUtilsSpec')
@Controller(SecurityUtilsSpec.controllerPath)
class SecurityUtilsController {

    @Produces(MediaType.TEXT_PLAIN)
    @Secured("isAnonymous()")
    @Get("/authenticated")
    boolean authenticated() {
        SecurityUtils.isAuthenticated()
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Secured("isAnonymous()")
    @Get("/currentuser")
    String currentuser() {
        Optional<String> str = SecurityUtils.username()
        str.map { m -> m}.orElse("Anonymous")
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Secured("isAnonymous()")
    @Get("/roles{?role}")
    Boolean roles(@Nullable String role) {
        SecurityUtils.hasRole(role)
    }
}
