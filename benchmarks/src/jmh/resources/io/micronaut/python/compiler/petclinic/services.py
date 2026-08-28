from typing import Annotated

from jakarta.inject import Inject, Singleton

from configuration import ClinicConfiguration, NotificationConfiguration
from forms import OwnerForm, PetForm, VisitForm
from models import Owner, Pet, Veterinarian, Visit
from repositories import OwnerRepository, PetRepository, VeterinarianRepository, VisitRepository


@Singleton
class ClinicService:
    owner_repository: Annotated[OwnerRepository, Inject]
    pet_repository: Annotated[PetRepository, Inject]
    visit_repository: Annotated[VisitRepository, Inject]
    veterinarian_repository: Annotated[VeterinarianRepository, Inject]
    configuration: Annotated[ClinicConfiguration, Inject]
    notifications: Annotated[NotificationConfiguration, Inject]

    def find_owner(self, owner_id: int) -> Owner | None:
        return self.owner_repository.find_by_id(owner_id)

    def find_owners(self, last_name: str) -> list[Owner]:
        return self.owner_repository.find_by_last_name(last_name)

    def create_owner(self, form: OwnerForm) -> Owner:
        owner = Owner(
            first_name=form.first_name,
            last_name=form.last_name,
            city=form.city,
            telephone=form.telephone,
        )
        return self.owner_repository.save(owner)

    def create_pet(self, form: PetForm) -> Pet:
        pet = Pet(
            name=form.name,
            pet_type=form.pet_type,
            owner_id=form.owner_id,
            birth_year=form.birth_year,
        )
        return self.pet_repository.save(pet)

    def create_visit(self, form: VisitForm) -> Visit:
        visit = Visit(pet_id=form.pet_id, description=form.description, visit_date=form.visit_date)
        return self.visit_repository.save(visit)

    def owner_visits(self, owner_id: int) -> list[Visit]:
        visits = []
        for pet in self.pet_repository.find_by_owner_id(owner_id):
            visits.extend(self.visit_repository.find_by_pet_id(pet.id or 0))
        return visits

    def veterinarians(self) -> list[Veterinarian]:
        return self.veterinarian_repository.find_all()
