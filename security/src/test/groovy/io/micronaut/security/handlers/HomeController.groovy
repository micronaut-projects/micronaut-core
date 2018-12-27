package io.micronaut.security.handlers

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule

@Requires(property = "spec.name", value = "RedirectRejectionHandlerSpec")
@Controller("/")
class HomeController {

    @Secured(SecurityRule.IS_ANONYMOUS)
    @Produces(MediaType.TEXT_PLAIN)
    @Get
    String index() {
        'open'
    }

    @Secured(SecurityRule.IS_ANONYMOUS)
    @Produces(MediaType.TEXT_PLAIN)
    @Get("/login")
    String login() {
        'login'
    }

    @Secured(SecurityRule.IS_ANONYMOUS)
    @Produces(MediaType.TEXT_PLAIN)
    @Get("/forbidden")
    String forbidden() {
        'forbidden'
    }

    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Produces(MediaType.TEXT_PLAIN)
    @Get("/secured")
    String secured() {
        'secured'
    }

    @Secured("ROLE_ADMIN")
    @Produces(MediaType.TEXT_PLAIN)
    @Get("/admin")
    String admin() {
        'admin'
    }
}
