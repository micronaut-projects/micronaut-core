from micronaut.context.annotation import Requires
# tag::clazz[]
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get, Produces

from .OutOfStockException import OutOfStockException


@Requires(property="spec.name", value="ExceptionHandlerSpec")
@Controller("/books")
class BookController:

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/stock/{isbn}")
    def stock(self, isbn: str) -> int:
        raise OutOfStockException()
# end::clazz[]
