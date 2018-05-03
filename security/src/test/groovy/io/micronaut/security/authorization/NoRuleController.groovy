package io.micronaut.security.authorization

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Requires(property = 'spec.name', value = 'authorization')
@Controller("/noRule")
class NoRuleController {

    @Get("/index")
    String index() {
        "index"
    }
}
