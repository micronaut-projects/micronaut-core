package io.micronaut.docs.respondingnotfound;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.reactivex.Maybe;

import java.util.Map;

@Requires(property = "spec.name", value = "respondingnotfound")
//tag::clazz[]
@Controller("/books")
class BooksController {

    @Get("/stock/{isbn}")
    Map stock(String isbn) {
        null //<1>
    }

    @Get("/maybestock/{isbn}")
    Maybe<Map> maybestock(String isbn) {
        Maybe.empty() //<2>
    }
}
//end::clazz[]
