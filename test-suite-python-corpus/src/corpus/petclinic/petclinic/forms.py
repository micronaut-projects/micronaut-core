"""Form DTOs used for HTML form binding and validation.

Micronaut can bind ``application/x-www-form-urlencoded`` request bodies into
Python dataclasses when controller parameters are annotated with ``Body``.
These DTOs intentionally allow ``None`` defaults so empty forms can be rendered
and partially submitted forms can be rebound after validation errors.

Validation constraints from Jakarta Validation are attached with
``typing.Annotated``. Controllers call Micronaut's injected ``Validator`` bean
to evaluate these constraints before mapping forms to persistent entities.
"""

from dataclasses import dataclass
from typing import Annotated

from java.time import LocalDate
from jakarta.validation.constraints import NotBlank, Pattern, Size
from micronaut.serde.annotation import Serdeable


@Serdeable
@dataclass
class OwnerForm:
    """Create/update form for ``Owner``."""

    id: int | None = None
    firstName: Annotated[str | None, NotBlank(message="First name is required"), Size(min=1, max=30, message="First name must be between 1 and 30 characters")] = None
    lastName: Annotated[str | None, NotBlank(message="Last name is required"), Size(min=1, max=30, message="Last name must be between 1 and 30 characters")] = None
    address: Annotated[str | None, NotBlank(message="Address is required"), Size(min=1, max=255, message="Address must be between 1 and 255 characters")] = None
    city: Annotated[str | None, NotBlank(message="City is required"), Size(min=1, max=80, message="City must be between 1 and 80 characters")] = None
    telephone: Annotated[str | None, NotBlank(message="Telephone is required"), Pattern(regexp=r"\d{10}", message="Telephone must be a 10-digit number")] = None


@Serdeable
@dataclass
class PetForm:
    """Create/update form for ``Pet``.

    The form carries ``typeId`` instead of a nested ``PetType`` object because
    HTML ``select`` elements submit scalar values.
    """

    id: int | None = None
    name: Annotated[str | None, NotBlank(message="Pet name is required"), Size(min=1, max=30, message="Pet name must be between 1 and 30 characters")] = None
    # HTML sends empty controls as strings. Keeping these as strings lets
    # validation render the form again instead of failing body conversion.
    birthDate: Annotated[str | None, NotBlank(message="Birth date is required")] = None
    typeId: Annotated[str | None, NotBlank(message="Pet type is required")] = None
    birthDateValue: LocalDate | None = None


@Serdeable
@dataclass
class VisitForm:
    """Create form for ``Visit``."""

    id: int | None = None
    date: Annotated[str | None, NotBlank(message="Visit date is required")] = None
    description: Annotated[str | None, NotBlank(message="Description is required"), Size(min=1, max=255, message="Description must be between 1 and 255 characters")] = None
    dateValue: LocalDate | None = None
