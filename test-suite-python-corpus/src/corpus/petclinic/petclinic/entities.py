"""Micronaut Data entities used by the PetClinic sample.

The classes in this module are regular Python dataclasses with Micronaut
annotations attached through ``typing.Annotated``. Pyronaut reads these
annotations at build time and generates the Micronaut bean metadata needed for
Micronaut Data JDBC and Micronaut Serialization.

Key concepts demonstrated here:

* ``@MappedEntity`` maps a Python class to a database table.
* ``@Id`` and ``@GeneratedValue`` identify generated primary keys.
* ``@MappedProperty`` pins Python attribute names to explicit column names only
  when the convention-based name would be wrong or ambiguous.
* ``@Relation`` models foreign-key relationships between entities.
* ``@Serdeable`` lets entities be serialized for JSON responses.
"""

from dataclasses import dataclass
from typing import Annotated

from java.time import LocalDate
from micronaut.core.annotation import Introspected
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, MappedProperty, Relation
from micronaut.serde.annotation import Serdeable


@Serdeable
@MappedEntity("PET_TYPES")
@dataclass
class PetType:
    """Lookup table entity for values such as cat, dog, and lizard."""

    name: str
    id: Annotated[int | None, Id, GeneratedValue] = None


@Serdeable
@MappedEntity("SPECIALITIES")
@dataclass
class Speciality:
    """Lookup table entity for veterinarian specialities."""

    name: str
    id: Annotated[int | None, Id, GeneratedValue] = None


@Serdeable
@MappedEntity("OWNERS")
@dataclass
class Owner:
    """A PetClinic owner.

    All business fields are required. Only the generated database ID defaults
    to ``None`` so new instances clearly represent unsaved rows.
    """

    firstName: str
    lastName: str
    address: str
    city: str
    telephone: str
    id: Annotated[int | None, Id, GeneratedValue] = None

    def is_new(self) -> bool:
        return self.id is None


@Serdeable
@MappedEntity("PETS")
@dataclass
class Pet:
    """A pet belongs to one owner and has one pet type.

    ``Relation.Kind.MANY_TO_ONE`` tells Micronaut Data that ``type`` and
    ``owner`` are stored as foreign keys. Repository methods use ``@Join`` to
    fetch these non-null relationships when the UI needs them.
    """

    name: str
    birthDate: LocalDate
    type: Annotated[PetType, Relation(Relation.Kind.MANY_TO_ONE), MappedProperty("TYPE_ID")]
    owner: Annotated[Owner, Relation(Relation.Kind.MANY_TO_ONE), MappedProperty("OWNER_ID")]
    id: Annotated[int | None, Id, GeneratedValue] = None

    def is_new(self) -> bool:
        return self.id is None

    def get_type_id(self) -> int | None:
        return self.type.id if self.type is not None else None

    def get_owner_id(self) -> int | None:
        return self.owner.id if self.owner is not None else None


@Serdeable
@MappedEntity("VISITS")
@dataclass
class Visit:
    """A veterinary visit for a pet."""

    date: Annotated[LocalDate, MappedProperty("VISIT_DATE")]
    description: str
    pet: Annotated[Pet, Relation(Relation.Kind.MANY_TO_ONE), MappedProperty("PET_ID")]
    id: Annotated[int | None, Id, GeneratedValue] = None

    def is_new(self) -> bool:
        return self.id is None


@Serdeable
@MappedEntity("VETS")
@dataclass
class Vet:
    """A veterinarian. Specialities are joined through ``VetSpeciality``."""

    firstName: str
    lastName: str
    id: Annotated[int | None, Id, GeneratedValue] = None


@Serdeable
@Introspected
@dataclass
class VetWithSpecialities:
    """Projection row returned by the vet list aggregate query."""

    id: int
    firstName: str
    lastName: str
    specialityRows: str | None


@Serdeable
@MappedEntity("VET_SPECIALITIES")
@dataclass
class VetSpeciality:
    """Join-table row linking a veterinarian to a speciality."""

    vet: Annotated[Vet, Relation(Relation.Kind.MANY_TO_ONE), MappedProperty("VET_ID")]
    speciality: Annotated[Speciality, Relation(Relation.Kind.MANY_TO_ONE), MappedProperty("SPECIALITY_ID")]
    id: Annotated[int | None, Id, GeneratedValue] = None
