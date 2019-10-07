package io.micronaut.docs.server.exception;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;

@Requires(property = "spec.name", value = "ExceptionHandlerSpec")
//tag::clazz[]
@Controller("/books")
public class BookController {

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/stock/{isbn}")
    Integer stock(String isbn) {
        throw new OutOfStockException();
    }
}
//end::clazz[]
