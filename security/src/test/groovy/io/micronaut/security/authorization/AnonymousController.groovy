package io.micronaut.security.authorization

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller
class AnonymousController {

    @Get("/hello")
    String hello() {
        "You are anonymous"
    }
}
