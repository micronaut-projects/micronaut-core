import builtins
from dataclasses import dataclass
from typing import Annotated, Protocol

from java.util import UUID
from micronaut.context.annotation import Requires
from micronaut.context.python.scope import ContextPooled
from micronaut.data.annotation import AutoPopulated, Id, Join, MappedEntity, MappedProperty, Relation
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository
from micronaut.http.annotation import Controller, Get


@MappedEntity("item_owners")
@dataclass
class ItemOwner:
    name: str
    id: Annotated[UUID | None, Id, AutoPopulated] = None


@MappedEntity("owned_items")
@dataclass
class OwnedItem:
    title: str
    owner: Annotated[ItemOwner, Relation(Relation.Kind.MANY_TO_ONE), MappedProperty("owner_id")]
    id: Annotated[UUID | None, Id, AutoPopulated] = None


@Requires(property="spec.name", value="OwnedItemRepositorySpec")
@JdbcRepository(dialect=Dialect.H2)
class ItemOwnerRepository(CrudRepository[ItemOwner, UUID], Protocol):
    pass


@Requires(property="spec.name", value="OwnedItemRepositorySpec")
@JdbcRepository(dialect=Dialect.H2)
class OwnedItemRepository(CrudRepository[OwnedItem, UUID], Protocol):
    @Join(value="owner", type=Join.Type.FETCH)
    def findById(self, id: UUID) -> OwnedItem | None: ...


# The routes run in a pooled context, while the entities the repository returns are generated Java
# objects whose Python objects Java created in the primary context.
@Requires(property="spec.name", value="OwnedItemRepositorySpec")
@Controller("/owned-items")
@ContextPooled
class OwnedItemController:
    def __init__(self, items: OwnedItemRepository):
        self.items = items

    @Get("/{itemId}/owner-id")
    def owner_id(self, itemId: UUID) -> str:
        owner_id = self.items.findById(itemId).orElse(None).owner.id
        return f"{type(owner_id).__module__}.{type(owner_id).__name__}|{owner_id}|{builtins.__MN_CTX_ID__}"

    @Get("/{itemId}/owned-by/{ownerId}")
    def owned_by(self, itemId: UUID, ownerId: UUID) -> bool:
        owner = self.items.findById(itemId).orElse(None).owner
        return str(owner.id) == str(ownerId) and owner.id == ownerId and hash(owner.id) == hash(ownerId)
