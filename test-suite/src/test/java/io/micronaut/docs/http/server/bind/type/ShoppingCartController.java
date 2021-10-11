package io.micronaut.docs.http.server.bind.type;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.HashMap;
import java.util.Map;

@Controller("/customBinding")
public class ShoppingCartController {

    // tag::method[]
    @Get("/typed")
    public HttpResponse<?> loadCart(ShoppingCart shoppingCart) { //<1>
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("sessionId", shoppingCart.getSessionId());
        responseMap.put("total", shoppingCart.getTotal());

        return HttpResponse.ok(responseMap);
    }
    // end::method[]
}
