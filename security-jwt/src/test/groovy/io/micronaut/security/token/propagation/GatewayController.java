package io.micronaut.security.token.propagation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.reactivex.Flowable;

@Requires(property = "spec.name", value = "tokenpropagation.gateway")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api")
public class GatewayController {

    private final BooksClient booksClient;
    private final InventoryClient inventoryClient;

    public GatewayController(BooksClient booksClient,
                             InventoryClient inventoryClient) {
        this.booksClient = booksClient;
        this.inventoryClient = inventoryClient;
    }

    @Get("/gateway")
    Flowable<Book> findAll() {
        return booksClient.fetchBooks()
                .flatMapMaybe(b -> inventoryClient.inventory(b.getIsbn())
                        .filter(stock -> stock > 0)
                        .map(stock -> {
                            b.setStock(stock);
                            return b;
                        })
                );
    }
}
