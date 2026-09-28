import java

from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from micronaut.retry.annotation import Retryable

from .Book import Book

IOException = java.type("java.io.IOException")
UncheckedIOException = java.type("java.io.UncheckedIOException")


@Requires(property="spec.name", value="NamedRetrySpec")
@Singleton
class NamedRetryBookService:
    def __init__(self):
        self.calls = 0

    # tag::named[]
    @Retryable(name="books")  # <1>
    def find_book(self, title: str) -> Book:
        # ...
    # end::named[]
        return self._fail_twice(title)

    # tag::override[]
    @Retryable(name="books", attempts="1")  # <1>
    def get_book(self, title: str) -> Book:
        # ...
    # end::override[]
        return self._fail_twice(title)

    def reset(self) -> int:
        calls = self.calls
        self.calls = 0
        return calls

    def _fail_twice(self, title: str) -> Book:
        self.calls += 1
        if self.calls < 3:
            raise UncheckedIOException(IOException("unavailable"))
        return Book(title)
