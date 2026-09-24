from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get


@Requires(property="spec.name", value="DocumentedPlaceholderSpec")
@Controller("/placeholders/books")
class BookController:
    @Get("/{id}", produces=MediaType.TEXT_PLAIN)
    def find(self, id: int) -> str:
        return f"Book {id}"
