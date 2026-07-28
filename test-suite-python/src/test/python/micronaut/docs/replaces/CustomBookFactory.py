from .BookFactory import BookFactory
from micronaut.context.annotation import Factory, Replaces
from .BookService import Book

# tag::class[]
@Factory
@Replaces(factory = BookFactory)
class CustomBookFactory:
    def other_book(self) -> Book:
        return Book("An OK Novel")
# end::class[]
