"""Application service layer.

``ClinicService`` is a Micronaut bean that coordinates repository calls and
contains workflow-oriented operations used by the controllers. Keeping these
operations in a service demonstrates normal Micronaut dependency injection in
Python and keeps route handlers small.

The bean is ``@Prototype`` to show explicit bean scoping. Micronaut injects the
repository fields into each service instance based on the ``Annotated[..., Inject]``
declarations.
"""

from typing import Annotated

from jakarta.inject import Inject
from jakarta.transaction import Transactional
from micronaut.context.annotation import Prototype

from .entities import Owner, Pet, PetType, Speciality, VetWithSpecialities, Visit
from .repositories import (
    OwnerRepository,
    PetRepository,
    PetTypeRepository,
    SpecialityRepository,
    VetRepository,
    VisitRepository,
)


@Prototype
class ClinicService:
    """Facade over repositories for PetClinic workflows."""

    owner_repository: Annotated[OwnerRepository, Inject]
    pet_repository: Annotated[PetRepository, Inject]
    pet_type_repository: Annotated[PetTypeRepository, Inject]
    visit_repository: Annotated[VisitRepository, Inject]
    vet_repository: Annotated[VetRepository, Inject]
    speciality_repository: Annotated[SpecialityRepository, Inject]

    def find_owner_by_id(self, owner_id: int) -> Owner | None:
        """Return one owner or ``None`` when no row exists."""

        return self.owner_repository.findOneById(owner_id)

    def find_owner_by_last_name(self, last_name: str) -> list[Owner]:
        return list(self.owner_repository.findByLastName(last_name))

    def find_all_owners(self) -> list[Owner]:
        return list(self.owner_repository.findAllOrdered())

    @Transactional
    def save_owner(self, owner: Owner) -> Owner:
        """Insert new owners and update existing owners in one call."""

        if owner.id is None:
            return self.owner_repository.save(owner)
        return self.owner_repository.update(owner)

    def find_pet_by_id(self, pet_id: int) -> Pet | None:
        return self.pet_repository.findOneById(pet_id)

    def find_pet_types(self) -> list[PetType]:
        return list(self.pet_type_repository.findAllOrderByName())

    def find_pet_type_by_id(self, type_id: int) -> PetType | None:
        return self.pet_type_repository.findOneById(type_id)

    @Transactional
    def save_pet(self, pet: Pet) -> Pet:
        """Insert new pets and update existing pets in one call."""

        if pet.id is None:
            return self.pet_repository.save(pet)
        return self.pet_repository.update(pet)

    def find_visits_by_pet_id(self, pet_id: int) -> list[Visit]:
        return list(self.visit_repository.findByPetIdOrderByDateDesc(pet_id))

    @Transactional
    def save_visit(self, visit: Visit) -> Visit:
        """Persist a visit inside a Micronaut transaction."""

        if visit.id is None:
            return self.visit_repository.save(visit)
        return self.visit_repository.update(visit)

    def find_all_vets(self) -> list[VetWithSpecialities]:
        return list(self.vet_repository.findAllWithSpecialities())

    def owner_detail_model(self, owner: Owner) -> list[dict]:
        """Build the view model used by the owner details template.

        The service shapes nested owner, pet, and visit data into dictionaries
        that are easy for the Jinjava templates to consume.
        """

        if owner.id is None:
            return []
        pets = []
        loaded_pets = list(self.pet_repository.findByOwnerIdOrderByName(owner.id))
        for pet in loaded_pets:
            visits = [
                {"id": visit.id, "date": str(visit.date or ""), "description": visit.description}
                for visit in self.visit_repository.findByPetIdOrderByDateDesc(pet.id)
            ]
            pets.append({
                "id": pet.id,
                "name": pet.name,
                "birthDate": str(pet.birthDate or ""),
                "type": pet.type.name if pet.type is not None else "",
                "visits": sorted(visits, key=lambda visit: str(visit["date"] or "")),
            })
        return sorted(pets, key=lambda pet: pet["name"].lower())

    def vet_models(self) -> list[dict]:
        """Build both HTML and JSON representations for veterinarian lists."""

        vets = list(self.vet_repository.findAllWithSpecialities())
        models = []
        for vet in vets:
            specialities = _parse_speciality_rows(vet.specialityRows)
            speciality_names = [speciality["name"] for speciality in specialities]
            models.append({
                "id": vet.id,
                "firstName": vet.firstName,
                "lastName": vet.lastName,
                "specialities": specialities,
                "specialitiesAsString": ", ".join(speciality_names) if speciality_names else "none",
            })
        return models

    def find_all_specialities(self) -> list[Speciality]:
        return list(self.speciality_repository.findAllOrderByName())


def _parse_speciality_rows(value: str | None) -> list[dict]:
    """Decode ``LISTAGG`` speciality rows from the vet aggregate query."""

    if not value:
        return []
    specialities = []
    for row in str(value).split("|"):
        if not row or ":" not in row:
            continue
        speciality_id, name = row.split(":", 1)
        if not speciality_id:
            continue
        specialities.append({"id": int(speciality_id), "name": name})
    return specialities
