from abc import abstractmethod
from typing import Annotated

import java

from .Pet import Pet
from .PetOperations import PetOperations

# tag::imports[]
from micronaut.core.async_.annotation import SingleResult
from micronaut.http.annotation import Post
from micronaut.http.client.annotation import Client
from jakarta.validation.constraints import Min, NotBlank
# end::imports[]

Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Client("/pets")  # <1>
class PetClient(PetOperations):  # <2>

    @SingleResult
    @Post
    @abstractmethod
    def save(self, name: Annotated[str, NotBlank], age: Annotated[int, Min(1)]) -> Publisher[Pet]:  # <3>
        pass
# end::class[]
