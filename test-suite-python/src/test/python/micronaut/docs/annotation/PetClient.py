from abc import abstractmethod
from typing import Annotated

import java

from .PetOperations import PetOperations

# tag::imports[]
from micronaut.http.annotation import Post
from micronaut.http.client.annotation import Client
from jakarta.validation.constraints import Min, NotBlank
# end::imports[]

Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Client("/pets")  # <1>
class PetClient(PetOperations):  # <2>

    # TODO: Re-enable @SingleResult when Python can import io.micronaut.core.async.annotation.SingleResult.
    # @SingleResult
    @Post
    @abstractmethod
    def save(self, name: Annotated[str, NotBlank], age: Annotated[int, Min(1)]) -> Publisher:  # <3>
        pass
# end::class[]
