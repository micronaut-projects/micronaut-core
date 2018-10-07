package io.micronaut.security.token.propagation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import io.reactivex.Maybe;

@Requires(property = "spec.name", value = "tokenpropagation.gateway")
@Client("inventory")
public interface InventoryClient {

    @Get("/api/inventory/{isbn}")
    public Maybe<Integer> inventory(String isbn);
}
