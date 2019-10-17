package io.micronaut.docs.server.binding

// tag::imports[]
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.validation.Validated
import javax.validation.Valid
// end::imports[]

// tag::class[]
@Controller("/api")
open class BookmarkController {

    @Get("/bookmarks/list{?paginationCommand*}")
    open fun list(@Valid paginationCommand: PaginationCommand): HttpStatus {
        return HttpStatus.OK
    }
}
// end::class[]
