package io.micronaut.docs.http.server.bind.type

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/customBinding")
class ShoppingCartController {

    // tag::method[]
    @Get("/typed")
    HttpResponse<Map<String, Object>> loadCart(ShoppingCart shoppingCart) { //<1>
        HttpResponse.ok(
                sessionId: shoppingCart.sessionId,
                total: shoppingCart.total)
    }
    // end::method[]
}
