package io.micronaut.docs.http.server.bind.annotation

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/customBinding")
class ShoppingCartController {

    // tag::method[]
    @Get("/annotated")
    HttpResponse<String> checkSession(@ShoppingCart Long sessionId) { //<1>
        HttpResponse.ok("Session:${sessionId}".toString())
    }
    // end::method
}
