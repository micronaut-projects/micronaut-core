package io.micronaut.multitenancy.propagation.httpheader

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.reactivex.Flowable

@Requires(property = 'spec.name', value = 'multitenancy.httpheader.gateway')
@Controller("/")
class GatewayController {

    private final BookFetcher bookFetcher

    GatewayController(BookFetcher bookFetcher) {
        this.bookFetcher = bookFetcher
    }

    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Get("/")
    Flowable<Book> index() {
        return bookFetcher.findAll()
    }
}
