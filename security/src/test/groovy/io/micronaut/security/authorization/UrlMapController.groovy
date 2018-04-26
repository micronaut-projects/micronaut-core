package io.micronaut.security.authorization

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.authentication.Authentication

import java.security.Principal

@Controller("/urlMap")
class UrlMapController {

    @Get("/admin")
    String admin() {
        "You have admin"
    }

    @Get("/authenticated")
    String authenticated(Authentication authentication) {
        "${authentication.name} is authenticated"
    }

    @Get("/principal")
    String authenticated(Principal principal) {
        "${principal.name} is authenticated"
    }
}
