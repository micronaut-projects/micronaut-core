package io.micronaut.docs.http.server.exception;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.annotation.Error;

@Requires(property = "spec.name", value = "ExceptionHandlerSpec")
//tag::clazz[]
@Controller("/books")
public class BookController {
    @Produces(MediaType.TEXT_PLAIN)
    @Get("/stock/{isbn}")
    Integer stock(String isbn) {
        throw new OutOfStockException();
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/null-pointer")
    Integer npe() {
        throw new NullPointerException();
    }

    @Error(exception = NullPointerException.class)
    @Produces(MediaType.TEXT_PLAIN)
    @Status(HttpStatus.MULTI_STATUS)
    String npeHandler() {
        return "NPE";
    }
}
//end::clazz[]
