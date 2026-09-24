"""Item service.

Corresponds to ``api/routes/items.py`` in the upstream template, minus the HTTP
concerns. Pagination is a ``Pageable`` handed to the repository, so the count
query is generated rather than written out per endpoint.
"""

from java.util import UUID
from jakarta.inject import Singleton
from jakarta.transaction import Transactional
from micronaut.data.model import Page, Pageable

from ..dto import ItemCreate, ItemUpdate
from ..entities import Item, User
from ..repositories import ItemRepository


@Singleton
class ItemService:
    def __init__(self, items: ItemRepository):
        self.items = items

    def by_id(self, item_id: UUID) -> Item | None:
        # As in users.py: an id read back from an entity is a Python uuid.UUID and must be
        # converted before it is used as a query parameter.
        return self.items.findById(UUID.fromString(str(item_id))).orElse(None)

    def list_for(self, user: User, pageable: Pageable) -> Page[Item]:
        """Superusers see every item; everyone else sees their own."""
        if user.isSuperuser:
            return self.items.findAll(pageable)
        return self.items.findByOwnerId(user.id, pageable)

    @staticmethod
    def is_owned_by(item: Item, user: User) -> bool:
        return item.owner is not None and str(item.owner.id) == str(user.id)

    @staticmethod
    def may_access(item: Item, user: User) -> bool:
        return user.isSuperuser or ItemService.is_owned_by(item, user)

    @Transactional
    def create(self, data: ItemCreate, owner: User) -> Item:
        return self.items.save(
            Item(title=data.title, description=data.description, owner=owner)
        )

    @Transactional
    def update(self, item: Item, data: ItemUpdate) -> Item:
        if data.title is not None:
            item.title = data.title
        if data.description is not None:
            item.description = data.description
        return self.items.update(item)

    @Transactional
    def delete(self, item: Item) -> None:
        self.items.delete(item)
