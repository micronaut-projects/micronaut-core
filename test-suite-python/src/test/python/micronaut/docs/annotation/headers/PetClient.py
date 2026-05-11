from abc import abstractmethod

import java

from micronaut.docs.annotation.Pet import Pet
from micronaut.docs.annotation.PetOperations import PetOperations

# tag::imports[]
from micronaut.core.async_.annotation import SingleResult
from micronaut.http.annotation import Get, Header
from micronaut.http.client.annotation import Client
# end::imports[]

Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Client("/pets")
@Header(name="X-Pet-Client", value="${pet.client.id}")
class PetClient(PetOperations):

    @SingleResult
    @abstractmethod
    def save(self, name: str, age: int) -> Publisher[Pet]:
        pass

    @SingleResult
    @Get("/{name}")
    @abstractmethod
    def get(self, name: str) -> Publisher[Pet]:
        pass
# end::class[]
