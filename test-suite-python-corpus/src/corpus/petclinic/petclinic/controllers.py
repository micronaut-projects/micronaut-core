"""HTTP controllers for the PetClinic sample.

This module demonstrates Pyronaut's script-style routing model. Functions
decorated with ``@Get`` and ``@Post`` become Micronaut routes, and module-level
``Annotated[..., Inject]`` variables are injected by Micronaut.

Important concepts shown here:

* ``@View`` renders the returned model with Micronaut Views.
* ``Annotated[T, Body]`` binds submitted form data into Python DTOs.
* ``QueryValue`` binds query parameters with defaults.
* ``HttpResponse.redirect`` and ``HttpResponse.notFound`` create explicit HTTP
  responses when a route should not render its default view.
* The controller depends on ``ClinicService`` and ``FormMapper`` instead of
  reaching into repositories directly.
"""

from datetime import date
from typing import Annotated

from jakarta.inject import Inject
from jakarta.validation import Validator
from java.net import URI
from java.time import LocalDate
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Body, Get, Post, Produces, QueryValue
from micronaut.views import View

from .forms import OwnerForm, PetForm, VisitForm
from .mapper import FormMapper
from .services import ClinicService

clinic_service: Annotated[ClinicService, Inject]
"""Injected application service facade."""

form_mapper: Annotated[FormMapper, Inject]
"""Injected compile-time mapper bean."""

validator: Annotated[Validator, Inject]
"""Injected Jakarta Validation entry point."""


def redirect_to(location: str):
    """Build a redirect response using Java's ``URI`` type."""

    return HttpResponse.redirect(URI.create(location))


def validation_errors(form) -> dict[str, str]:
    """Convert Jakarta ``ConstraintViolation`` objects into template data."""

    errors = {}
    for violation in validator.validate(form):
        field = str(violation.getPropertyPath())
        if "." in field:
            field = field.rsplit(".", 1)[-1]
        if field:
            errors[field] = str(violation.getMessage())
    for field, label in (("birthDate", "Birth date"), ("date", "Visit date")):
        value = getattr(form, field, None)
        if value:
            try:
                date.fromisoformat(value)
            except ValueError:
                errors[field] = f"{label} must be a valid date"
    return errors


def pet_type_for(form: PetForm, errors: dict[str, str]):
    try:
        return clinic_service.find_pet_type_by_id(int(form.typeId)) if form.typeId else None
    except ValueError:
        errors["typeId"] = "Invalid pet type"
        return None


def parse_form_date(value: str | None) -> LocalDate:
    assert value is not None
    return LocalDate.parse(value)


@Get("/")
@View("welcome")
def welcome() -> dict:
    """Render the welcome page.

    Returning a dict gives the view renderer its model. This page has no dynamic
    data, so the model is empty.
    """

    return {}


@Get("/locale")
def locale(backUrl: Annotated[str, QueryValue(defaultValue="/")] = "/"):
    return redirect_to(backUrl or "/")


@Get("/owners/find")
@View("owners/findOwners")
def init_find_form(notFound: Annotated[bool, QueryValue(defaultValue="false")] = False) -> dict:
    return {"owner": OwnerForm(), "notFound": notFound}


@Get("/owners")
@View("owners/ownersList")
def process_find_form(lastName: Annotated[str, QueryValue(defaultValue="")] = ""):
    """Search owners and follow the PetClinic redirect behavior."""

    owners = clinic_service.find_all_owners() if not lastName else clinic_service.find_owner_by_last_name(lastName)
    if len(owners) == 0:
        return redirect_to("/owners/find?notFound=true")
    if len(owners) == 1:
        return redirect_to(f"/owners/{owners[0].id}")
    return {"owners": owners, "lastName": lastName}


@Get("/owners/list")
@View("owners/ownersList")
def show_owner_list(lastName: Annotated[str, QueryValue(defaultValue="")] = "") -> dict:
    owners = clinic_service.find_all_owners() if not lastName else clinic_service.find_owner_by_last_name(lastName)
    return {"owners": owners, "lastName": lastName}


@Get("/owners/new")
@View("owners/createOrUpdateOwnerForm")
def init_owner_creation_form() -> dict:
    return {"owner": OwnerForm(), "isNew": True, "validationErrors": {}}


@Post(value="/owners/new", consumes=MediaType.APPLICATION_FORM_URLENCODED)
@View("owners/createOrUpdateOwnerForm")
def process_owner_creation_form(form: Annotated[OwnerForm, Body]):
    """Bind, validate, map, persist, and redirect after owner creation."""

    errors = validation_errors(form)
    if errors:
        return {"owner": form, "isNew": True, "validationErrors": errors}
    owner = clinic_service.save_owner(form_mapper.to_owner(form))
    return redirect_to(f"/owners/{owner.id}")


@Get("/owners/{ownerId}/edit")
@View("owners/createOrUpdateOwnerForm")
def init_owner_update_form(ownerId: int) -> dict:
    owner = clinic_service.find_owner_by_id(ownerId)
    if owner is None:
        return {"error": "Owner not found", "isNew": False, "validationErrors": {}}
    return {"owner": form_mapper.to_owner_form(owner), "ownerId": ownerId, "isNew": False, "validationErrors": {}}


@Post(value="/owners/{ownerId}/edit", consumes=MediaType.APPLICATION_FORM_URLENCODED)
@View("owners/createOrUpdateOwnerForm")
def process_owner_update_form(ownerId: int, form: Annotated[OwnerForm, Body]):
    existing = clinic_service.find_owner_by_id(ownerId)
    if existing is None:
        return HttpResponse.notFound()
    errors = validation_errors(form)
    if errors:
        return {"owner": form, "ownerId": ownerId, "isNew": False, "validationErrors": errors}
    clinic_service.save_owner(form_mapper.update_owner(existing, form))
    return redirect_to(f"/owners/{ownerId}")


@Get("/owners/{ownerId}")
@View("owners/ownerDetails")
def show_owner(ownerId: int) -> dict:
    owner = clinic_service.find_owner_by_id(ownerId)
    if owner is None:
        return {"error": "Owner not found"}
    return {"owner": owner, "pets": clinic_service.owner_detail_model(owner)}


@Get("/owners/{ownerId}/pets/new")
@View("pets/createOrUpdatePetForm")
def init_pet_creation_form(ownerId: int) -> dict:
    owner = clinic_service.find_owner_by_id(ownerId)
    if owner is None or owner.id != ownerId:
        return {"error": "Owner not found"}
    return {"pet": PetForm(), "owner": owner, "types": clinic_service.find_pet_types(), "isNew": True, "validationErrors": {}}


@Post(value="/owners/{ownerId}/pets/new", consumes=MediaType.APPLICATION_FORM_URLENCODED)
@View("pets/createOrUpdatePetForm")
def process_pet_creation_form(ownerId: int, form: Annotated[PetForm, Body]):
    """Create a pet from a form DTO.

    ``PetForm`` carries a scalar ``typeId``. The controller resolves that ID to
    the ``PetType`` entity before calling the generated mapper.
    """

    owner = clinic_service.find_owner_by_id(ownerId)
    if owner is None:
        return HttpResponse.notFound()
    errors = validation_errors(form)
    pet_type = pet_type_for(form, errors)
    if form.typeId and pet_type is None:
        errors["typeId"] = "Invalid pet type"
    if errors:
        return {"pet": form, "owner": owner, "types": clinic_service.find_pet_types(), "isNew": True, "validationErrors": errors}
    form.birthDateValue = parse_form_date(form.birthDate)
    pet = form_mapper.to_pet(form, owner, pet_type)
    clinic_service.save_pet(pet)
    return redirect_to(f"/owners/{ownerId}")


@Get("/owners/{ownerId}/pets/{petId}/edit")
@View("pets/createOrUpdatePetForm")
def init_pet_update_form(ownerId: int, petId: int) -> dict:
    pet = clinic_service.find_pet_by_id(petId)
    if pet is None:
        return {"error": "Pet not found"}
    owner = pet.owner or clinic_service.find_owner_by_id(ownerId)
    return {"pet": form_mapper.to_pet_form(pet), "petId": petId, "owner": owner, "types": clinic_service.find_pet_types(), "isNew": False, "validationErrors": {}}


@Post(value="/owners/{ownerId}/pets/{petId}/edit", consumes=MediaType.APPLICATION_FORM_URLENCODED)
@View("pets/createOrUpdatePetForm")
def process_pet_update_form(ownerId: int, petId: int, form: Annotated[PetForm, Body]):
    owner = clinic_service.find_owner_by_id(ownerId)
    pet = clinic_service.find_pet_by_id(petId)
    if owner is None or pet is None:
        return HttpResponse.notFound()
    errors = validation_errors(form)
    pet_type = pet_type_for(form, errors)
    if form.typeId and pet_type is None:
        errors["typeId"] = "Invalid pet type"
    if errors:
        return {"pet": form, "petId": petId, "owner": owner, "types": clinic_service.find_pet_types(), "isNew": False, "validationErrors": errors}
    form.birthDateValue = parse_form_date(form.birthDate)
    updated = form_mapper.update_pet(pet, form, owner, pet_type)
    clinic_service.save_pet(updated)
    return redirect_to(f"/owners/{ownerId}")


@Get("/owners/{ownerId}/pets/{petId}/visits/new")
@View("pets/createOrUpdateVisitForm")
def init_visit_creation_form(ownerId: int, petId: int) -> dict:
    pet = clinic_service.find_pet_by_id(petId)
    if pet is None:
        return {"error": "Pet not found"}
    return {"visit": VisitForm(), "pet": pet, "owner": pet.owner, "validationErrors": {}}


@Post(value="/owners/{ownerId}/pets/{petId}/visits/new", consumes=MediaType.APPLICATION_FORM_URLENCODED)
@View("pets/createOrUpdateVisitForm")
def process_visit_creation_form(ownerId: int, petId: int, form: Annotated[VisitForm, Body]):
    pet = clinic_service.find_pet_by_id(petId)
    if pet is None:
        return HttpResponse.notFound()
    errors = validation_errors(form)
    if errors:
        return {"visit": form, "pet": pet, "owner": pet.owner, "validationErrors": errors}
    form.dateValue = parse_form_date(form.date)
    visit = form_mapper.to_visit(form, pet)
    clinic_service.save_visit(visit)
    return redirect_to(f"/owners/{ownerId}")


@Get("/vets")
@View("vets/vetList")
def show_vet_list() -> dict:
    return {"vets": clinic_service.vet_models()}


@Get("/vets/html")
@View("vets/vetList")
def show_vet_list_html() -> dict:
    return show_vet_list()


@Get("/vets/json")
@Produces(MediaType.APPLICATION_JSON)
def show_vet_list_json() -> list:
    """Return the same vet model as JSON.

    ``@Produces`` selects the JSON media type instead of view rendering.
    """

    return clinic_service.vet_models()
