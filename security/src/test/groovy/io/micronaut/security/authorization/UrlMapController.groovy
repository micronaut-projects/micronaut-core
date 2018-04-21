package io.micronaut.security.authorization

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/urlMap")
class UrlMapController {

    @Get("/admin")
    String admin() {
        "You have admin"
    }

    @Get("/authenticated")
    String authenticated() {
        "You are authenticated"
    }
}
