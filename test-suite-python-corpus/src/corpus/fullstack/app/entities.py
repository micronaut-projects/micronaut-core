"""Micronaut Data entities.

These are ordinary Python dataclasses. The Micronaut annotations attached
through ``typing.Annotated`` are read at build time by ``pyronaut process``,
which generates the Micronaut Data and Micronaut Serialization metadata. There
is no ORM session, no lazy loading and no runtime reflection: the SQL for every
repository method in ``repositories.py`` is generated while the project
compiles.

Compare ``backend/app/models.py`` in the upstream FastAPI template, where the
same two tables are described with SQLModel.
"""

from dataclasses import dataclass
from typing import Annotated

from java.time import Instant
from java.util import UUID
from micronaut.data.annotation import (
    AutoPopulated,
    DateCreated,
    Id,
    MappedEntity,
    MappedProperty,
    Relation,
)
from micronaut.serde.annotation import Serdeable


@Serdeable
@MappedEntity("users")
@dataclass
class User:
    """An application user.

    ``@AutoPopulated`` generates the UUID primary key on insert, so a freshly
    constructed ``User`` carries ``id=None`` and is unambiguously unsaved.
    """

    email: str
    hashedPassword: str
    fullName: str | None = None
    isActive: bool = True
    isSuperuser: bool = False
    createdAt: Annotated[Instant | None, DateCreated] = None
    id: Annotated[UUID | None, Id, AutoPopulated] = None

    def is_new(self) -> bool:
        return self.id is None


@Serdeable
@MappedEntity("items")
@dataclass
class Item:
    """An item owned by exactly one user.

    The owner is a ``MANY_TO_ONE`` relation stored as the ``owner_id`` foreign
    key. Deletion cascades in the database, not here — see V1__initial_schema.sql.
    """

    title: str
    owner: Annotated[User, Relation(Relation.Kind.MANY_TO_ONE), MappedProperty("owner_id")]
    description: str | None = None
    createdAt: Annotated[Instant | None, DateCreated] = None
    id: Annotated[UUID | None, Id, AutoPopulated] = None

    def is_new(self) -> bool:
        return self.id is None
