from abc import ABC, abstractmethod
from typing import Annotated

import java

from .Pet import Pet

# tag::imports[]
from micronaut.http.annotation import Post
from micronaut.validation import Validated
from jakarta.validation.constraints import Min, NotBlank
# end::imports[]

Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Validated
class PetOperations(ABC):
    # tag::save[]
    @Post
    # TODO: Re-enable @SingleResult when Python can import io.micronaut.core.async.annotation.SingleResult.
    # @SingleResult
    @abstractmethod
    def save(self, name: Annotated[str, NotBlank], age: Annotated[int, Min(1)]) -> Publisher:
        pass
    # end::save[]
# end::class[]
