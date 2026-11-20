"""Micronaut Data JDBC repositories.

Repositories are declared as ``Protocol`` classes carrying only method
signatures. ``pyronaut process`` parses each method name into a query and
generates the implementation at build time. A method name that cannot be parsed
is a build failure, not a runtime one.
"""

from typing import Protocol

from java.util import UUID
from micronaut.data.annotation import Join, Query
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model import Page, Pageable
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository, PageableRepository

from .entities import Item, User


@JdbcRepository(dialect=Dialect.MYSQL)
class UserRepository(CrudRepository[User, UUID], PageableRepository[User, UUID], Protocol):
    """User lookups for authentication, the admin screens and signup."""

    def findByEmail(self, email: str) -> User | None: ...

    def existsByEmail(self, email: str) -> bool: ...

    @Query(
        "SELECT * FROM users ORDER BY created_at DESC",
        countQuery="SELECT COUNT(*) FROM users",
        nativeQuery=True,
    )
    def findAllOrdered(self, pageable: Pageable) -> Page[User]: ...


@JdbcRepository(dialect=Dialect.MYSQL)
class ItemRepository(CrudRepository[Item, UUID], PageableRepository[Item, UUID], Protocol):
    """Item queries.

    Every method that returns an ``Item`` joins its owner, so ``Item.owner`` is
    never a half-populated stub. This is the Micronaut Data answer to the
    lazy-loading footguns an ORM leaves in place.
    """

    @Join(value="owner", type=Join.Type.FETCH)
    def findById(self, id: UUID) -> Item | None: ...

    @Join(value="owner", type=Join.Type.FETCH)
    def findByOwnerId(self, ownerId: UUID, pageable: Pageable) -> Page[Item]: ...

    @Join(value="owner", type=Join.Type.FETCH)
    def findAll(self, pageable: Pageable) -> Page[Item]: ...

    def countByOwnerId(self, ownerId: UUID) -> int: ...
