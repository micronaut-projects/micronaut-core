package io.micronaut.docs.http.server.exception.aterrorexceptionhandlerprecedence;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;

@Requires(property = "spec.name", value = "AtErrorExceptionHandlerPrecedenceSpec")
@Controller("/outofstock")
public class OutOfStockController {

    @Error(exception = OutOfStockException.class, global = true)
    public HttpResponse handleOutOfStock(HttpRequest request) {
        return HttpResponse.ok(-1);
    }
}
