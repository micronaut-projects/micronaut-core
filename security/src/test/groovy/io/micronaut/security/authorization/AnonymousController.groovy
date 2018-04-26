package io.micronaut.security.authorization

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

import javax.annotation.Nullable
import java.security.Principal

@Controller
class AnonymousController {

    @Get("/hello")
    String hello(@Nullable Principal principal) {
        "You are ${principal != null ? principal.getName() : 'anonymous'}"
    }
}
