from micronaut.http import HttpStatus, MediaType
from micronaut.http.annotation import Controller, Post, Status

from .Book import Book


@Controller("/amazon")
class BookController:

    @Status(HttpStatus.CREATED)
    @Post(
        value="/book/{title}",
        consumes=[MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED],
    )
    def save(self, title: str) -> Book:
        return Book(title)
