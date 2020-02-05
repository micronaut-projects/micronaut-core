package io.micronaut.docs.server.binding;

// tag::imports[]
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.validation.Valid;
// end::imports[]

// tag::class[]
@Controller("/api")
public class BookmarkController {

    @Get("/bookmarks/list{?paginationCommand*}")
    public HttpStatus list(@Valid @Nullable PaginationCommand paginationCommand) {
        return HttpStatus.OK;
    }
}
// end::class[]
