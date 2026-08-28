import java
from typing import Annotated

from .Pet import Pet
from .PetOperations import PetOperations

# tag::imports[]
from micronaut.core.async_.annotation import SingleResult
from micronaut.http.annotation import Controller, Post
from micronaut.validation import Validated
from jakarta.validation.constraints import Min, NotBlank
# end::imports[]

Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Validated
@Controller("/pets")
class PetController(PetOperations):

    @SingleResult
    @Post
    def save(self, name: Annotated[str, NotBlank], age: Annotated[int, Min(1)]) -> Publisher[Pet]:
        pet = Pet(name=name, age=age)
        # save to database or something
        return Mono.just(pet)
# end::class[]
