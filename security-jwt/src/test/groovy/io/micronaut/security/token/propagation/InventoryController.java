package io.micronaut.security.token.propagation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

@Requires(property = "spec.name", value = "tokenpropagation.inventory")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api")
public class InventoryController {

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/inventory/{isbn}")
    public HttpResponse<Integer> inventory(String isbn) {
        if (isbn.equals("1491950358")) {
            return HttpResponse.ok(2);
        } else if (isbn.equals("1680502395")) {
            return HttpResponse.ok(3);
        } else {
            return HttpResponse.notFound();
        }
    }
}