from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from javax.sql import DataSource

class BookService:
    ...

# tag::requires[]
@Singleton
@Requires(beans = DataSource)
@Requires(property = "datasource.url")
class JdbcBookService(BookService):
    def __init__(self, data_source: DataSource):
        self.data_source = data_source
# end::requires[]
