package io.micronaut.docs.http.server.bind.annotation

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import java.util.*

@Controller("/customBinding")
class ShoppingCartController {

    // tag::method[]
    @Get("/annotated")
    fun checkSession(@ShoppingCart sessionId: Long): HttpResponse<String> { //<1>
        return HttpResponse.ok("Session:$sessionId")
    }
    // end::method[]
}
