package io.micronaut.security.token.jwt.signature.rsagenerationvalidation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.util.Collections;
import java.util.List;

@Requires(property = "spec.name", value = "rsajwtbooks")
@Controller("/books")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BooksController {

    @Get
    List<Book> findAll() {
        return Collections.singletonList(new Book("Building Microservices"));
    }
}
