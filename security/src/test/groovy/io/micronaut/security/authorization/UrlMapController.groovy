package io.micronaut.security.authorization

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.authentication.Authentication

@Controller("/urlMap")
class UrlMapController {

    @Get("/admin")
    String admin() {
        "You have admin"
    }

    @Get("/authenticated")
    String authenticated(Authentication authentication) {
        "${authentication.id} is authenticated"
    }
}
