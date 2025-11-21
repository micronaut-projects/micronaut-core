from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context.annotation import Property
from micronaut.test.annotation import MockBean
from .JdbcBookService import BookService
from javax.sql import DataSource
from jakarta.inject import Inject
from typing import Annotated

@Property(name = "datasource.url", value = "test")
@MicronautTest
class BookServiceSpec:
    bookService : Annotated[BookService, Inject] = None

    @Test
    def test_book_service(self):
        assert self.bookService is not None, "should have a book service"
        assert self.bookService.__class__.__name__ == 'JdbcBookService', "should be a JDBC book service"
        assert self.bookService.data_source != None, "should have a datasource"

    @MockBean
    def test_data_source(self) -> DataSource:
        return TestDataSource()


class TestDataSource(DataSource):
    def __init__(self):
        pass
