package io.micronaut.multitenancy.propagation.httpheader

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.reactivex.Flowable

@Requires(property = 'spec.name', value = 'multitenancy.httpheader.gorm')
@CompileStatic
@Controller("/api")
class BooksController {

    private final BookService bookService

    BooksController(BookService bookService) {
        this.bookService = bookService
    }

    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Get("/books")
    Flowable<Book> books() {
        Flowable.fromIterable(bookService.list())
    }
}
