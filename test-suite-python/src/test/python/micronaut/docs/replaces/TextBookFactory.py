from .BookFactory import BookFactory
from micronaut.context.annotation import Factory, Replaces
from .BookFactory import TextBook

# tag::class[]
@Factory
class TextBookFactory:
    @Replaces(value = TextBook, factory = BookFactory)
    def other_book(self) -> BookFactory:
        return BookFactory("Learning 305")
# end::class[]
