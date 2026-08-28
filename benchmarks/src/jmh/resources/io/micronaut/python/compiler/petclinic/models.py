from dataclasses import dataclass, field
from typing import Annotated

from jakarta.validation.constraints import Min, NotBlank
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Owner:
    id: int | None = None
    first_name: Annotated[str, NotBlank] = ""
    last_name: Annotated[str, NotBlank] = ""
    address: str = ""
    city: str = ""
    telephone: Annotated[str, NotBlank] = ""
    pets: list["Pet"] = field(default_factory=list)

    def display_name(self) -> str:
        return f"{self.first_name} {self.last_name}".strip()


@Introspected
@dataclass
class Pet:
    id: int | None = None
    name: Annotated[str, NotBlank] = ""
    pet_type: Annotated[str, NotBlank] = ""
    owner_id: Annotated[int, Min(1)] = 1
    birth_year: Annotated[int, Min(1900)] = 2020
    visits: list["Visit"] = field(default_factory=list)

    def is_young(self, year: int) -> bool:
        return year - self.birth_year < 5


@Introspected
@dataclass
class Visit:
    id: int | None = None
    pet_id: Annotated[int, Min(1)] = 1
    description: Annotated[str, NotBlank] = ""
    visit_date: Annotated[str, NotBlank] = ""


@Introspected
@dataclass
class Veterinarian:
    id: int | None = None
    first_name: Annotated[str, NotBlank] = ""
    last_name: Annotated[str, NotBlank] = ""
    specialities: list[str] = field(default_factory=list)

    def display_name(self) -> str:
        return f"Dr. {self.first_name} {self.last_name}".strip()
