package io.micronaut.views.model.security;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.views.View;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Requires(property = "spec.name", value = "SecurityViewModelProcessorSpec")
//tag::class[]
@Controller("/")
public class BooksController {

    @Secured(SecurityRule.IS_AUTHENTICATED)
    @View("securitydecorator")
    @Get
    public Map<String, Object> index() {
        Map<String, Object> model = new HashMap<>();
        model.put("books", Arrays.asList("Developing Microservices"));
        return model;
    }
}
//end::class[]