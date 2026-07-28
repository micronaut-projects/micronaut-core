from typing import Annotated

# tag::imports[]
import java
from jakarta.validation import Valid
from micronaut.http.annotation import Controller, Get, RequestBean

from .MovieTicketBean import MovieTicketBean

HttpStatus = java.type("io.micronaut.http.HttpStatus")
# end::imports[]


# tag::class[]
@Controller("/api")
class MovieTicketController:

    # You can also omit query parameters like:
    # @Get("/movie/ticket/{movieId}
    @Get("/movie/ticket/{movieId}{?minPrice,maxPrice}")
    def list(self, bean: Annotated[MovieTicketBean, Valid, RequestBean]) -> HttpStatus:
        return HttpStatus.OK
# end::class[]
