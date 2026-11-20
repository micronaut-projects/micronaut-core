"""Item CRUD.

Ownership is not expressible as a role, so it stays an explicit check here, the
same way the upstream template does it. Everything that *is* expressible as a
role — "must be signed in", "must be a superuser" — is left to ``@Secured``.
"""

from typing import Annotated

from jakarta.validation import Valid
from java.util import UUID
from micronaut.http import HttpResponse, HttpStatus
from micronaut.http.annotation import Body, Controller, Delete, Get, Post, Put, QueryValue
from micronaut.security.annotation import Secured
from micronaut.security.authentication import Authentication
from micronaut.security.rules import SecurityRule

from ..dto import ItemCreate, ItemPublic, ItemUpdate, ItemsPublic, Message
from ..mappers import item_public, items_public
from ..paging import DEFAULT_PAGE_SIZE, page_request
from ..services.items import ItemService
from ..services.users import UserService


@Controller("/api/v1/items")
@Secured(SecurityRule.IS_AUTHENTICATED)
class ItemController:
    def __init__(self, items: ItemService, users: UserService):
        self.items = items
        self.users = users

    def _current(self, authentication: Authentication):
        return self.users.by_id(UUID.fromString(str(authentication.getName())))

    @Get
    def list_items(
        self,
        authentication: Authentication,
        page: Annotated[int, QueryValue(defaultValue="0")] = 0,
        size: Annotated[int, QueryValue(defaultValue="100")] = DEFAULT_PAGE_SIZE,
    ) -> ItemsPublic:
        """List items.

        A superuser sees every item; everyone else sees only their own.
        Paginated with `page` and `size`.
        """
        result = self.items.list_for(self._current(authentication), page_request(page, size))
        return items_public(result.getContent(), result.getTotalSize())

    @Get("/{itemId}")
    def read_item(self, itemId: UUID, authentication: Authentication) -> HttpResponse:
        """Fetch one item by id.

        Returns 404 when the item does not exist and 403 when it belongs to
        someone else.
        """
        item = self.items.by_id(itemId)
        if item is None:
            return HttpResponse.notFound()
        if not self.items.may_access(item, self._current(authentication)):
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(
                Message(message="The user doesn't have enough privileges")
            )
        return HttpResponse.ok(item_public(item))

    @Post
    def create_item(
        self, authentication: Authentication, body: Annotated[ItemCreate, Body, Valid]
    ) -> HttpResponse:
        """Create an item owned by the authenticated user."""
        item = self.items.create(body, self._current(authentication))
        return HttpResponse.status(HttpStatus.CREATED).body(item_public(item))

    @Put("/{itemId}")
    def update_item(
        self,
        itemId: UUID,
        authentication: Authentication,
        body: Annotated[ItemUpdate, Body, Valid],
    ) -> HttpResponse:
        """Update an item. Only the owner, or a superuser, may do so."""
        item = self.items.by_id(itemId)
        if item is None:
            return HttpResponse.notFound()
        if not self.items.may_access(item, self._current(authentication)):
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(
                Message(message="The user doesn't have enough privileges")
            )
        return HttpResponse.ok(item_public(self.items.update(item, body)))

    @Delete("/{itemId}")
    def delete_item(self, itemId: UUID, authentication: Authentication) -> HttpResponse:
        """Delete an item. Only the owner, or a superuser, may do so."""
        item = self.items.by_id(itemId)
        if item is None:
            return HttpResponse.notFound()
        if not self.items.may_access(item, self._current(authentication)):
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(
                Message(message="The user doesn't have enough privileges")
            )
        self.items.delete(item)
        return HttpResponse.ok(Message(message="Item deleted successfully"))
