from dataclasses import dataclass
from typing import Annotated

from jakarta.persistence import Entity, GeneratedValue, Id, Table


# tag::clazz[]
@Entity
@Table(name="books")
@dataclass
class Book:
    title: str | None = None
    pages: int = 0
    id: Annotated[int | None, Id, GeneratedValue] = None
# end::clazz[]
