"""Request and response bodies.

``@Serdeable`` generates a serializer and deserializer for each dataclass at
build time — no runtime model construction, no ``pydantic-core``. The
``jakarta.validation`` constraints attached through ``Annotated`` are enforced
by Micronaut when a controller parameter is marked ``@Valid``, so a controller
never has to call a validator by hand.

These mirror the request/response models in the upstream template's
``backend/app/models.py``.
"""

from dataclasses import dataclass, field
from typing import Annotated

from jakarta.validation.constraints import Email, NotBlank, Size
from micronaut.serde.annotation import Serdeable

# NOTE: every constraint below is written out in full, deliberately.
#
# Annotation arguments are read from source by the processor, not evaluated, so
# a shared constant — `PASSWORD = Size(min=8, max=128)` used as
# `Annotated[str, NotBlank, PASSWORD]` — is silently dropped. The field then has
# no length constraint at all and the API happily accepts a four-character
# password. The same rule bites computed `defaultValue` arguments; see
# controllers/users.py. Repetition here is the safe option.


# --------------------------------------------------------------------------
# Users
# --------------------------------------------------------------------------
@Serdeable
@dataclass
class UserCreate:
    email: Annotated[str, NotBlank, Email, Size(max=255)]
    password: Annotated[str, NotBlank, Size(min=8, max=128, message="Password must be between 8 and 128 characters")]
    fullName: Annotated[str | None, Size(max=255)] = None
    isActive: bool = True
    isSuperuser: bool = False


@Serdeable
@dataclass
class UserRegister:
    email: Annotated[str, NotBlank, Email, Size(max=255)]
    password: Annotated[str, NotBlank, Size(min=8, max=128, message="Password must be between 8 and 128 characters")]
    fullName: Annotated[str | None, Size(max=255)] = None


@Serdeable
@dataclass
class UserUpdate:
    email: Annotated[str | None, Email, Size(max=255)] = None
    password: Annotated[str | None, Size(min=8, max=128, message="Password must be between 8 and 128 characters")] = None
    fullName: Annotated[str | None, Size(max=255)] = None
    isActive: bool | None = None
    isSuperuser: bool | None = None


@Serdeable
@dataclass
class UserUpdateMe:
    email: Annotated[str | None, Email, Size(max=255)] = None
    fullName: Annotated[str | None, Size(max=255)] = None


@Serdeable
@dataclass
class UpdatePassword:
    currentPassword: Annotated[str, NotBlank, Size(min=8, max=128, message="Password must be between 8 and 128 characters")]
    newPassword: Annotated[str, NotBlank, Size(min=8, max=128, message="Password must be between 8 and 128 characters")]


@Serdeable
@dataclass
class UserPublic:
    id: str
    email: str
    isActive: bool
    isSuperuser: bool
    fullName: str | None = None
    createdAt: str | None = None


@Serdeable
@dataclass
class UsersPublic:
    data: list[UserPublic] = field(default_factory=list)
    count: int = 0


# --------------------------------------------------------------------------
# Items
# --------------------------------------------------------------------------
@Serdeable
@dataclass
class ItemCreate:
    title: Annotated[str, NotBlank, Size(min=1, max=255)]
    description: Annotated[str | None, Size(max=255)] = None


@Serdeable
@dataclass
class ItemUpdate:
    title: Annotated[str | None, Size(min=1, max=255)] = None
    description: Annotated[str | None, Size(max=255)] = None


@Serdeable
@dataclass
class ItemPublic:
    id: str
    title: str
    ownerId: str
    description: str | None = None
    createdAt: str | None = None


@Serdeable
@dataclass
class ItemsPublic:
    data: list[ItemPublic] = field(default_factory=list)
    count: int = 0


# --------------------------------------------------------------------------
# Login and password recovery
# --------------------------------------------------------------------------
@Serdeable
@dataclass
class NewPassword:
    token: Annotated[str, NotBlank]
    newPassword: Annotated[str, NotBlank, Size(min=8, max=128, message="Password must be between 8 and 128 characters")]


@Serdeable
@dataclass
class Message:
    message: str


@Serdeable
@dataclass
class ApiError:
    """The single error shape for the whole API.

    The upstream template returns ``{"detail": "..."}`` for errors but
    ``{"detail": [...]}`` for validation failures, so its generated TypeScript
    client has to branch on the type of ``detail``. One shape is easier to
    consume and easier to document. See PLAN.md section 1.3.
    """

    message: str
    errors: dict[str, str] = field(default_factory=dict)
