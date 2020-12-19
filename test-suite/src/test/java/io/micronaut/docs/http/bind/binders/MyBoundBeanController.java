package io.micronaut.docs.http.bind.binders;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.HashMap;
import java.util.Map;

@Controller("/customBinding")
public class MyBoundBeanController {

    // tag::typed[]
    @Get("/typed")
    public HttpResponse<?> loadCart(ShoppingCart shoppingCart) { //<1>
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("sessionId", shoppingCart.getSessionId());
        responseMap.put("total", shoppingCart.getTotal());

        return HttpResponse.ok(responseMap);
    }
    // end::typed[]

    // tag::annotated[]
    @Get("/annotated")
    HttpResponse<String> checkSession(@MyBindingAnnotation Long sessionId) { //<1>
        return HttpResponse.ok("Session:" + sessionId);
    }
    // end::annotated
}
