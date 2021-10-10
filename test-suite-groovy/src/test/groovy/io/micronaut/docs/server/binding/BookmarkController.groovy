package io.micronaut.docs.server.binding

// tag::imports[]
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

import javax.annotation.Nullable
import javax.validation.Valid
// end::imports[]

// tag::class[]
@Controller("/api")
class BookmarkController {

    @Get("/bookmarks/list{?paginationCommand*}")
    HttpStatus list(@Valid @Nullable PaginationCommand paginationCommand) {
        HttpStatus.OK
    }
}
// end::class[]
