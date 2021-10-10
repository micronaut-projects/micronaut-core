package io.micronaut.docs.respondingnotfound

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.reactivex.Maybe

@Requires(property = "spec.name", value = "respondingnotfound")
//tag::clazz[]
@Controller("/books")
class BooksController {

    @Get("/stock/{isbn}")
    fun stock(isbn: String): Map<*, *>? {
        return null //<1>
    }

    @Get("/maybestock/{isbn}")
    fun maybestock(isbn: String): Maybe<Map<*, *>> {
        return Maybe.empty() //<2>
    }
}
//end::clazz[]
