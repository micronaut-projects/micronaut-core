from micronaut.context.annotation import Requires
from micronaut.http import HttpResponse, HttpStatus
from micronaut.http.annotation import Controller, Get

from .Book import Book


@Requires(property="spec.name", value="BindHttpClientExceptionBodySpec")
# tag::clazz[]
@Controller("/books")
class BooksController:

    @Get("/{isbn}")
    def find(self, isbn: str) -> HttpResponse:
        if isbn == "1680502395":
            body = {
                "status": 401,
                "error": "Unauthorized",
                "message": "No message available",
                "path": "/books/" + isbn,
            }
            return HttpResponse.status(HttpStatus.UNAUTHORIZED).body(body)

        return HttpResponse.ok(Book("1491950358", "Building Microservices"))
# end::clazz[]
