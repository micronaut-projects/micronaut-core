package io.micronaut.security.authorization

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/noRule")
class NoRuleController {

    @Get("/index")
    String index() {
        "index"
    }
}
