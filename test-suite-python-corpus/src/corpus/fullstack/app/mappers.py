"""Entity to DTO mapping.

Entities carry Java types — ``java.util.UUID`` keys and ``java.time.Instant``
timestamps — because that is what Micronaut Data reads and writes. The public
API speaks strings, so the conversion happens here rather than being repeated in
every controller.
"""

from .dto import ItemPublic, ItemsPublic, UserPublic, UsersPublic
from .entities import Item, User


def _text(value) -> str | None:
    return None if value is None else str(value)


def user_public(user: User) -> UserPublic:
    """Public projection of a user. Never includes the password hash."""
    return UserPublic(
        id=_text(user.id),
        email=user.email,
        isActive=user.isActive,
        isSuperuser=user.isSuperuser,
        fullName=user.fullName,
        createdAt=_text(user.createdAt),
    )


def users_public(users, count: int) -> UsersPublic:
    return UsersPublic(data=[user_public(user) for user in users], count=int(count))


def item_public(item: Item) -> ItemPublic:
    return ItemPublic(
        id=_text(item.id),
        title=item.title,
        ownerId=_text(item.owner.id) if item.owner is not None else None,
        description=item.description,
        createdAt=_text(item.createdAt),
    )


def items_public(items, count: int) -> ItemsPublic:
    return ItemsPublic(data=[item_public(item) for item in items], count=int(count))
