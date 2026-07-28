from .JdbcBookService import JdbcBookService
from micronaut.context.annotation import Requires, Replaces
from .BookService import Book, BookService
from jakarta.inject import Singleton

# tag::class[]
@Singleton
@Replaces(JdbcBookService)
class MockBookService(BookService):
    book_map : dict[str, Book] = {}

    def find_book(self, title: str) -> Book:
        return self.book_map.get(title)
# end::class[]
