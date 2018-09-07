package io.micronaut.security.atsecured

import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.Secured

@Requires(env = Environment.TEST)
@Requires(property = 'spec.name', value = 'AtSecuredAppliedToServiceMethodSpec')
@Controller(AtSecuredAppliedToServiceMethodSpec.controllerPath)
class BookController {

    protected final BookRepository bookRepository

    BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository
    }

    @Secured("isAuthenticated()")
    @Get("/books")
    Map<String, Object> list() {
        [books: bookRepository.findAll()]
    }
}
