package io.micronaut.security.authorization

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import java.security.Principal

@Controller
class AnonymousController {

    @Get("/hello")
    String hello(Optional<Principal> principal) {
        "You are ${principal.isPresent() ? principal.get().getName() : 'anonymous'}"
    }
}
