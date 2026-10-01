# tag::imports[]
from micronaut.http import MediaType
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client
# end::imports[]


# tag::class[]
@Client("/placeholders/books")
class BookClient:

    @Get("/{id}", consumes=MediaType.TEXT_PLAIN)
    def find(self, id: int) -> str:
        """Fetches the title of the book with the given id."""
        ...
# end::class[]
