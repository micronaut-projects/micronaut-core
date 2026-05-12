from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context.annotation import Property
from jakarta.inject import Inject
from typing import Annotated
from .BookService import Book, BookService

@Property(name = "datasource.url", value = "test")
@MicronautTest(transactional = False)
class BookServiceSpec:
    book_service : Annotated[BookService, Inject] = None

    @Test
    def test_book_service(self):
        assert self.book_service is not None, "should have a book service"
        self.book_service.book_map["An OK Novel"] = Book("An OK Novel")
        assert self.book_service.find_book("An OK Novel").title == "An OK Novel"
