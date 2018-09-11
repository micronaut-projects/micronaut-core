package io.micronaut.docs.respondingnotfound;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.Map;
import java.util.Optional;

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