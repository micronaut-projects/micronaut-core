package io.micronaut.docs.http.bind.binders

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import java.util.*

@Controller("/customBinding")
class MyBoundBeanController {

    // tag::typed[]
    @Get("/typed")
    fun loadCart(shoppingCart: ShoppingCart): HttpResponse<*> { //<1>
        val responseMap: MutableMap<String, String?> = HashMap()
        responseMap["sessionId"] = shoppingCart.sessionId
        responseMap["total"] = shoppingCart.total.toString()

        return HttpResponse.ok<Map<String, String?>>(responseMap)
    }
    // end::typed[]

    // tag::annotated[]
    @Get("/annotated")
    fun checkSession(@MyBindingAnnotation sessionId: Long): HttpResponse<String> { //<1>
        return HttpResponse.ok("Session:$sessionId")
    }
    // end::annotated
}
