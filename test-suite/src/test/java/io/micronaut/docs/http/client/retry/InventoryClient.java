package io.micronaut.docs.http.client.retry;

// tag::imports[]
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
// end::imports[]

// tag::class[]
@Client(id = "inventory") // <1>
public interface InventoryClient {

    @Get("/inventory/{item}") // <2>
    String stock(String item);
}
// end::class[]
