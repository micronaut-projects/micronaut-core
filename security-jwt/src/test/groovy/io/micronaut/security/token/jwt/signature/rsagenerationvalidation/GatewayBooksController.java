package io.micronaut.security.token.jwt.signature.rsagenerationvalidation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.util.List;

@Requires(property = "spec.name", value = "rsajwtgateway")
@Controller("/books")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class GatewayBooksController {

    private final BooksClient booksClient;

    public GatewayBooksController(BooksClient booksClient) {
        this.booksClient = booksClient;
    }

    @Get
    List<Book> findAll(@Header("Authorization") String authorization) {
        return booksClient.findAll(authorization);
    }
}
