from .BookService import Book
from micronaut.context.annotation import Factory
from jakarta.inject import Singleton
from dataclasses import dataclass

@dataclass
class TextBook:
    title : str

# tag::class[]
@Factory
class BookFactory:
    @Singleton
    def novel(self) -> Book:
        return Book("A Great Novel")

    @Singleton
    def text_book(self) -> TextBook:
        return TextBook("Learning 101")
# end::class[]
