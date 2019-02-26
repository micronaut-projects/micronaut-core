package io.micronaut.views

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller('/csp')
@Requires(property = 'spec.name', value = 'CspFilterSpec')
class TestController {

    @Get
    HttpResponse index() {
        HttpResponse.ok()
    }

    @Get("/ignore")
    HttpResponse ignore() {
        HttpResponse.ok()
    }
}
