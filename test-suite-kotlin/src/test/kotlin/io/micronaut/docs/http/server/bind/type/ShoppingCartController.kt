package io.micronaut.docs.http.server.bind.type

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/customBinding")
class ShoppingCartController {

    // tag::method[]
    @Get("/typed")
    fun loadCart(shoppingCart: ShoppingCart): HttpResponse<*> { //<1>
        return HttpResponse.ok(mapOf(
            "sessionId" to shoppingCart.sessionId,
            "total" to shoppingCart.total))
    }
    // end::method[]
}
