from abc import abstractmethod

import java

from micronaut.docs.annotation.PetOperations import PetOperations

# tag::imports[]
from micronaut.core.async_.annotation import SingleResult
from micronaut.http.client.annotation import Client
from micronaut.retry.annotation import Retryable
# end::imports[]

Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Client("/pets")
@Retryable
class PetClient(PetOperations):

    @SingleResult
    @abstractmethod
    def save(self, name: str, age: int) -> Publisher:
        pass
# end::class[]
