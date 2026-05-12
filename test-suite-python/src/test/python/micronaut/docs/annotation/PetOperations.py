from typing import Annotated

import java

from .Pet import Pet

# tag::imports[]
from micronaut.core.async_.annotation import SingleResult
from micronaut.http.annotation import Post
from micronaut.validation import Validated
from jakarta.validation.constraints import Min, NotBlank
# end::imports[]

Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Validated
class PetOperations:
    # tag::save[]
    @Post
    @SingleResult
    def save(self, name: Annotated[str, NotBlank], age: Annotated[int, Min(1)]) -> Publisher[Pet]:
        ...
    # end::save[]
# end::class[]
