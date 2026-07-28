from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context.annotation import Property
from micronaut.test.annotation import MockBean
from .JdbcBookService import BookService
from javax.sql import DataSource
from jakarta.inject import Inject
from typing import Annotated

@Property(name = "datasource.url", value = "test")
@MicronautTest(transactional = False)
class BookServiceSpec:
    book_service : Annotated[BookService, Inject] = None

    @Test
    def test_book_service(self):
        assert self.book_service is not None, "should have a book service"
        book_service = self.book_service
        if hasattr(book_service, "asPolyglotValue"):
            book_service = book_service.asPolyglotValue()
        assert book_service.__class__.__name__ == 'JdbcBookService', "should be a JDBC book service"
        assert self.book_service.data_source is not None, "should have a datasource"

    @MockBean
    def test_data_source(self) -> DataSource:
        return TestDataSource()


class TestDataSource(DataSource):
    def __init__(self):
        pass
