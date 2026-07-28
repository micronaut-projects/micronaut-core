from dataclasses import dataclass
from typing import Annotated

from jakarta.validation.constraints import Min, NotBlank
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class OwnerForm:
    first_name: Annotated[str, NotBlank]
    last_name: Annotated[str, NotBlank]
    city: Annotated[str, NotBlank]
    telephone: Annotated[str, NotBlank]


@Introspected
@dataclass
class PetForm:
    name: Annotated[str, NotBlank]
    pet_type: Annotated[str, NotBlank]
    owner_id: Annotated[int, Min(1)]
    birth_year: Annotated[int, Min(1900)] = 2020


@Introspected
@dataclass
class VisitForm:
    pet_id: Annotated[int, Min(1)]
    description: Annotated[str, NotBlank]
    visit_date: Annotated[str, NotBlank]
