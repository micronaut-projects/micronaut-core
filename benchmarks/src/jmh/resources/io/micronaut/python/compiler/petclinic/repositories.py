from jakarta.inject import Singleton

from models import Owner, Pet, Veterinarian, Visit


@Singleton
class OwnerRepository:
    def find_by_id(self, owner_id: int) -> Owner | None:
        return None

    def find_by_last_name(self, last_name: str) -> list[Owner]:
        return []

    def save(self, owner: Owner) -> Owner:
        return owner


@Singleton
class PetRepository:
    def find_by_id(self, pet_id: int) -> Pet | None:
        return None

    def find_by_owner_id(self, owner_id: int) -> list[Pet]:
        return []

    def save(self, pet: Pet) -> Pet:
        return pet


@Singleton
class VisitRepository:
    def find_by_pet_id(self, pet_id: int) -> list[Visit]:
        return []

    def save(self, visit: Visit) -> Visit:
        return visit


@Singleton
class VeterinarianRepository:
    def find_all(self) -> list[Veterinarian]:
        return []
