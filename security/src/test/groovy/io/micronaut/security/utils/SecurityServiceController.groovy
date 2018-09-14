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
@Requires(property = SecurityServiceSpec.SPEC_NAME_PROPERTY, value = 'SecurityServiceSpec')
@Controller(SecurityServiceSpec.controllerPath)
class SecurityServiceController {

    private final SecurityService securityService

    SecurityServiceController(SecurityService securityService) {
        this.securityService = securityService
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Secured("isAnonymous()")
    @Get("/authenticated")
    boolean authenticated() {
        securityService.isAuthenticated()
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Secured("isAnonymous()")
    @Get("/currentuser")
    String currentuser() {
        Optional<String> str = securityService.username()
        str.map { m -> m}.orElse("Anonymous")
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Secured("isAnonymous()")
    @Get("/roles{?role}")
    Boolean roles(@Nullable String role) {
        securityService.hasRole(role)
    }
}
