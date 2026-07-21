from typing import Annotated

from jakarta.inject import Inject
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Body, Get, Post, QueryValue

from forms import OwnerForm, PetForm, VisitForm
from services import ClinicService


clinic_service: Annotated[ClinicService, Inject]


@Get("/")
def welcome() -> dict:
    return {"application": "Pyronaut PetClinic"}


@Get("/owners")
def find_owners(last_name: Annotated[str, QueryValue(defaultValue="")] = "") -> dict:
    return {"owners": clinic_service.find_owners(last_name), "lastName": last_name}


@Get("/owners/{owner_id}")
def show_owner(owner_id: int):
    owner = clinic_service.find_owner(owner_id)
    if owner is None:
        return HttpResponse.notFound()
    return {"owner": owner, "visits": clinic_service.owner_visits(owner_id)}


@Post(value="/owners", consumes=MediaType.APPLICATION_JSON)
def create_owner(form: Annotated[OwnerForm, Body]):
    return HttpResponse.created(clinic_service.create_owner(form))


@Post(value="/pets", consumes=MediaType.APPLICATION_JSON)
def create_pet(form: Annotated[PetForm, Body]):
    return HttpResponse.created(clinic_service.create_pet(form))


@Post(value="/visits", consumes=MediaType.APPLICATION_JSON)
def create_visit(form: Annotated[VisitForm, Body]):
    return HttpResponse.created(clinic_service.create_visit(form))


@Get("/veterinarians")
def list_veterinarians() -> dict:
    return {"veterinarians": clinic_service.veterinarians()}
