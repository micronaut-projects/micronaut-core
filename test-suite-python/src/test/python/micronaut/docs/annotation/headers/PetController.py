from typing import Annotated

from micronaut.docs.annotation.Pet import Pet

from micronaut.http import HttpResponse
from micronaut.http.annotation import Controller, Get, Header


@Controller("/pets")
class PetController:

    @Get("/{name}")
    def get(self, name: str, clientId: Annotated[str, Header("X-Pet-Client")]) -> HttpResponse:
        pet = Pet(name=name, age=int(clientId))
        return HttpResponse.ok(pet).header("X-Pet-Client", clientId)
