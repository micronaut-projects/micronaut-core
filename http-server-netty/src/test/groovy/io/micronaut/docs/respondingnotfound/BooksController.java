package io.micronaut.docs.respondingnotfound;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.reactivex.Maybe;

import java.util.Map;

@Requires(property = "spec.name", value = "respondingnotfound")
//tag::clazz[]
@Controller("/books")
public class BooksController {

    @Get("/stock/{isbn}")
    public Map stock(String isbn) {
        return null; //<1>
    }

    @Get("/maybestock/{isbn}")
    public Maybe<Map> maybestock(String isbn) {
        return Maybe.empty(); //<2>
    }
}
//end::clazz[]
