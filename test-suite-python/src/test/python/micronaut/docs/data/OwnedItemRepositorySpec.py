import builtins
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .OwnedItemRepository import ItemOwner, ItemOwnerRepository, OwnedItem, OwnedItemRepository


# An entity reached through a fetch join reads the same in a pooled context as anywhere else: its
# UUID id is a uuid.UUID of that context, not the foreign object of the context Java created the
# entity's Python object in (whose str() was "UUID('...')").
# :test-suite-python:test --tests "*OwnedItemRepositorySpec*"
@MicronautTest(transactional=False)
@Property(name="spec.name", value="OwnedItemRepositorySpec")
@Property(name="micronaut.python.pool.enabled", value="true")
@Property(name="micronaut.python.pool.size", value="2")
@Property(name="datasources.default.url", value="jdbc:h2:mem:ownedItems;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
@Property(name="datasources.default.schema-generate", value="CREATE_DROP")
@Property(name="datasources.default.dialect", value="H2")
class OwnedItemRepositorySpec:
    owners: Annotated[ItemOwnerRepository, Inject] = None
    items: Annotated[OwnedItemRepository, Inject] = None
    client: Annotated[HttpClient, Inject, Client("/")] = None

    def _save(self):
        owner = self.owners.save(ItemOwner("Fred"))
        item = self.items.save(OwnedItem("Book", owner))
        return owner, item

    @Test
    def joinedOwnerIdIsAUuidOfThePooledContext(self) -> None:
        owner, item = self._save()
        for _ in range(4):
            description = self.client.toBlocking().retrieve(f"/owned-items/{item.id}/owner-id")
            id_type, text, context_id = description.split("|")
            # the route ran in a pooled context, not in the one of this test
            assert context_id != builtins.__MN_CTX_ID__, description
            assert id_type == "uuid.UUID", description
            assert text == str(owner.id), description

    @Test
    def joinedOwnerIdEqualsTheIdBoundToTheRoute(self) -> None:
        owner, item = self._save()
        for _ in range(4):
            owned = self.client.toBlocking().retrieve(f"/owned-items/{item.id}/owned-by/{owner.id}")
            assert owned == "true", owned
