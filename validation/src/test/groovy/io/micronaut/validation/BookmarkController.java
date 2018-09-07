package io.micronaut.validation;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

@Validated
@Controller("/api")
public class BookmarkController {

    @Get("/bookmarks{?offset,max,sort,order}")
    public HttpStatus index(@PositiveOrZero @Nullable Integer offset,
                            @Positive @Nullable Integer max,
                            @Nullable @Pattern(regexp = "name|href|title") String sort,
                            @Nullable @Pattern(regexp = "asc|desc|ASC|DESC") String order) {
        return HttpStatus.OK;
    }

    @Get("/bookmarks/list{?paginationCommand*}")
    public HttpStatus list(@Valid @Nullable PaginationCommand paginationCommand) {
        return HttpStatus.OK;
    }

}
