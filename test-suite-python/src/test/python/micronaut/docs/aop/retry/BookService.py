import java

from micronaut.retry.annotation import CircuitBreaker, Retryable

from .Book import Book


class BookService:
    # tag::simple[]
    @Retryable
    def list_books(self) -> list[Book]:
        # ...
    # end::simple[]
        return [Book("The Stand")]

    # tag::circuit[]
    @CircuitBreaker(reset="30s")
    def find_books(self) -> list[Book]:
        # ...
    # end::circuit[]
        return [Book("The Stand")]

    # tag::attempts[]
    @Retryable(attempts="5",
               delay="2s")
    def find_book(self, title: str) -> Book:
        # ...
    # end::attempts[]
        return Book(title)

    # tag::config[]
    @Retryable(attempts="${book.retry.attempts:3}",
               delay="${book.retry.delay:1s}")
    def get_book(self, title: str) -> Book:
        # ...
    # end::config[]
        return Book(title)

    # tag::reactive[]
    @Retryable
    def stream_books(self):
        # ...
    # end::reactive[]
        Flux = java.type("reactor.core.publisher.Flux")
        return Flux.just(Book("The Stand"))
