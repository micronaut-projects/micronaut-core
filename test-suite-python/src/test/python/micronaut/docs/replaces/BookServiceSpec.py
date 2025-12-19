from org.junit.jupiter.api import Test, Disabled
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context.annotation import Property
from jakarta.inject import Inject
from typing import Annotated
from .BookService import BookService

@Property(name = "datasource.url", value = "test")
@MicronautTest(transactional = False)
class BookServiceSpec:
    book_service : Annotated[BookService, Inject] = None

    @Test
    @Disabled("For some reason the ReplacesDefinition never loads")
    def test_book_service(self):
        assert self.book_service is not None, "should have a book service"
        assert self.book_service.__class__.__name__ == 'MockBookService', "should be a JDBC book service"

