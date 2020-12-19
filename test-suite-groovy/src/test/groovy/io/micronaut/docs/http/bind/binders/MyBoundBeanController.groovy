package io.micronaut.docs.http.bind.binders

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/customBinding")
class MyBoundBeanController {

    // tag::typed[]
    @Get("/typed")
    HttpResponse<?> loadCart(ShoppingCart shoppingCart) { //<1>
        Map<String, Object> responseMap = [:]
        responseMap.sessionId = shoppingCart.sessionId
        responseMap.total = shoppingCart.total

        return HttpResponse.ok(responseMap)
    }
    // end::typed[]

    // tag::annotated[]
    @Get("/annotated")
    HttpResponse<String> checkSession(@MyBindingAnnotation Long sessionId) { //<1>
        HttpResponse.ok("Session:${sessionId}".toString())
    }
    // end::annotated
}
