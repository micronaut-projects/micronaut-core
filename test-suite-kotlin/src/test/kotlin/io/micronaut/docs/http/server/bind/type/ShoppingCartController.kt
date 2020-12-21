package io.micronaut.docs.http.server.bind.type

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import java.util.*

@Controller("/customBinding")
class ShoppingCartController {

    // tag::method[]
    @Get("/typed")
    fun loadCart(shoppingCart: ShoppingCart): HttpResponse<*> { //<1>
        val responseMap: MutableMap<String, Any?> = HashMap()
        responseMap["sessionId"] = shoppingCart.sessionId
        responseMap["total"] = shoppingCart.total

        return HttpResponse.ok<Map<String, Any?>>(responseMap)
    }
    // end::method[]
}
