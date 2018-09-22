package io.micronaut.multitenancy.propagation.principal

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.utils.SecurityService

@Requires(property = 'spec.name', value = 'multitenancy.principal.gorm')
@CompileStatic
@Controller("/api")
class BooksController {

    private final BookService bookService
    private final SecurityService securityService

    BooksController(BookService bookService,
                    SecurityService securityService) {
        this.bookService = bookService
        this.securityService = securityService
    }

    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Get("/books")
    List<String> books() {
        List<Book> books = bookService.list()
        books*.title
    }
}
