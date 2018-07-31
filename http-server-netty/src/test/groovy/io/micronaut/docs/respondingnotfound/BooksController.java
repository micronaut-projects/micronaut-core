package io.micronaut.docs.respondingnotfound;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.reactivex.Maybe;

import java.util.Optional;

@Requires(property = "spec.name", value = "respondingnotfound")
//tag::clazz[]
@Controller("/books")
public class BooksController {
//end::clazz[]
    //tag::stock[]
    @Produces(MediaType.TEXT_PLAIN)
    @Get("/stock/{isbn}")
    public Boolean stock(String isbn) {
        Optional<Integer> bookInventoryOptional = bookInventoryByIsbn(isbn);
        if (!bookInventoryOptional.isPresent()) {
            return null; // <1>
        }
        Integer stock = bookInventoryOptional.get();
        return stock> 0 ? Boolean.TRUE : Boolean.FALSE;
    }
    //end::stock[]

    //tag::bookInventoryByIsbn[]
    private Optional<Integer> bookInventoryByIsbn(String isbn) {
        if(isbn.equals("1491950358")) {
            return Optional.of(4);

        } else if(isbn.equals("1680502395")) {
            return Optional.of(0);
        }
        return Optional.empty();
    }
    //end::bookInventoryByIsbn[]

    //tag::maybestock[]
    @Produces(MediaType.TEXT_PLAIN)
    @Get("/maybestock/{isbn}")
    public Maybe<Boolean> maybestock(String isbn) {
        Optional<Integer> bookInventoryOptional = bookInventoryByIsbn(isbn);
        if (!bookInventoryOptional.isPresent()) {
            return Maybe.empty();
        }
        Integer stock = bookInventoryOptional.get();
        return Maybe.just(stock> 0 ? Boolean.TRUE : Boolean.FALSE);
    }
    //end::maybestock[]
}
