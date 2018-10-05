package io.micronaut.security.token.propagation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.util.Arrays;
import java.util.List;

@Requires(property = "spec.name", value = "tokenpropagation.books")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api")
public class BooksController {
    @Get("/books")
    public List<Book> list() {
        return Arrays.asList(new Book("1491950358", "Building Microservices"),
                new Book("1680502395", "Release It!"));
    }
}
