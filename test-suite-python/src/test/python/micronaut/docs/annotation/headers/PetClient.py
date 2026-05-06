from abc import abstractmethod

import java

from micronaut.docs.annotation.PetOperations import PetOperations

# tag::imports[]
from micronaut.http.annotation import Get, Header
from micronaut.http.client.annotation import Client
# end::imports[]

Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Client("/pets")
@Header(name="X-Pet-Client", value="${pet.client.id}")
class PetClient(PetOperations):

    # TODO: Re-enable @SingleResult when Python can import io.micronaut.core.async.annotation.SingleResult.
    # @SingleResult
    @abstractmethod
    def save(self, name: str, age: int) -> Publisher:
        pass

    # TODO: Re-enable @SingleResult when Python can import io.micronaut.core.async.annotation.SingleResult.
    # @SingleResult
    @Get("/{name}")
    @abstractmethod
    def get(self, name: str) -> Publisher:
        pass
# end::class[]
