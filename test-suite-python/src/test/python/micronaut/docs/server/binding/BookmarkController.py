from typing import Annotated

# tag::imports[]
import java
from jakarta.validation import Valid
from micronaut.http.annotation import Controller, Get

from .PaginationCommand import PaginationCommand

HttpStatus = java.type("io.micronaut.http.HttpStatus")
# end::imports[]


# tag::class[]
@Controller("/api")
class BookmarkController:

    @Get("/bookmarks/list{?paginationCommand*}")
    def list(self, paginationCommand: Annotated[PaginationCommand | None, Valid]) -> HttpStatus:
        return HttpStatus.OK
# end::class[]
