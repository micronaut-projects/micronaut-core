from dataclasses import dataclass

from micronaut.core.annotation import Introspected, ReflectiveAccess


@ReflectiveAccess
@Introspected
@dataclass
class Book:
    isbn: str | None = None
    title: str | None = None
