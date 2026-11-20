from micronaut.context.annotation import Requires
from micronaut.http.annotation import Controller, Get


@Requires(property="spec.name", value="RequestMetricsTest")
@Controller("/books")
class BookController:

    @Get("/{id}")
    def book(self, id: str) -> str:
        return "book " + id
