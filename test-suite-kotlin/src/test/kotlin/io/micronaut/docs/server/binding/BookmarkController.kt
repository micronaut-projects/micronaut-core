package io.micronaut.docs.server.binding

// tag::imports[]

import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import javax.validation.Valid

// end::imports[]

/**
 * @author Puneet Behl
 * @since 1.0
 */
// tag::class[]
@Controller("/api")
class BookmarkController {
    // end::class[]

    // tag::listBooks[]
    @Get("/bookmarks/list{?paginationCommand*}")
    fun list(@Valid paginationCommand: PaginationCommand?): HttpStatus {
        return HttpStatus.OK
    }
    // end::listBooks[]
}
