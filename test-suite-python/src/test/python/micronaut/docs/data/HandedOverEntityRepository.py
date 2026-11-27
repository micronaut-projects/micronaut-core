import dataclasses
from dataclasses import dataclass
from enum import Enum
from typing import Annotated, Protocol

from micronaut.context.annotation import Requires
from micronaut.context.python.scope import ContextPooled
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository
from micronaut.http.annotation import Controller, Get


class ShelfStatus(Enum):
    TO_READ = "to_read"
    DONE = "done"


@MappedEntity("shelved_books")
@dataclass
class ShelvedBook:
    id: Annotated[int | None, Id, GeneratedValue]
    title: str
    pages: int
    status: ShelfStatus


@Requires(property="spec.name", value="HandedOverEntitySpec")
@JdbcRepository(dialect=Dialect.H2)
class ShelvedBookRepository(CrudRepository[ShelvedBook, int], Protocol):
    pass


def _describe(books) -> str:
    iterated = [b for b in books]
    first, sliced = books[0], books[0:2]
    return "|".join([
        ",".join(sorted({type(b).__name__ for b in iterated} | {type(first).__name__} | {type(b).__name__ for b in sliced})),
        str(all(isinstance(b, ShelvedBook) for b in iterated)),
        str(sorted(dataclasses.asdict(b)["title"] for b in iterated)),
        str(sum(1 for b in books if b.status == ShelfStatus.DONE)),
        str(sum(1 for b in books if b.status is ShelfStatus.DONE)),
    ])


def _edit_all(repository) -> str:
    books = repository.findAll()
    for b in books:
        b.title = b.title + "!"
        b.pages += 1
    repository.updateAll(books)
    return str(sorted((b.title, b.pages) for b in repository.findAll()))


def _optional(repository, book_id: int) -> str:
    found = repository.findById(book_id)
    book = found.orElse(None)
    same = found.get()
    thrown = found.orElseThrow()
    got = found.orElseGet(lambda: None)
    seen = []
    found.ifPresent(seen.append)
    book.title = book.title + "?"
    # the JavaBean accessors of the wrapper still answer on the Python object
    aliases = book.getPages() == book.pages
    book.setPages(book.pages + 100)
    repository.update(book)
    missing = repository.findById(-1)
    return "|".join([
        ",".join(sorted({type(b).__name__ for b in (book, same, thrown, got, seen[0])})),
        str(dataclasses.asdict(book)["pages"]),
        repository.findById(book_id).orElse(None).title,
        str(aliases and repository.findById(book_id).orElse(None).pages == book.pages),
        str(missing.orElse(None) is None),
        str(missing.orElseGet(lambda: "empty")),
    ])


# A route that runs in the context the entities' Python objects were created in, and one that runs in a
# pooled context, which is handed copies of those objects
@Requires(property="spec.name", value="HandedOverEntitySpec")
@Controller("/handed-over")
class HandedOverController:
    def __init__(self, books: ShelvedBookRepository):
        self.books = books

    @Get("/optional/{bookId}")
    def optional(self, bookId: int) -> str:
        return _optional(self.books, bookId)

    @Get("/describe")
    def describe(self) -> str:
        return _describe(self.books.findAll())

    @Get("/edit-all")
    def edit_all(self) -> str:
        return _edit_all(self.books)


@Requires(property="spec.name", value="HandedOverEntitySpec")
@Controller("/handed-over-pooled")
@ContextPooled
class PooledHandedOverController:
    def __init__(self, books: ShelvedBookRepository):
        self.books = books

    @Get("/optional/{bookId}")
    def optional(self, bookId: int) -> str:
        return _optional(self.books, bookId)

    @Get("/describe")
    def describe(self) -> str:
        return _describe(self.books.findAll())

    @Get("/edit-all")
    def edit_all(self) -> str:
        return _edit_all(self.books)
