import java

from micronaut.docs.annotation.Pet import Pet
from micronaut.docs.annotation.PetOperations import PetOperations

# tag::imports[]
from micronaut.core.async_.annotation import SingleResult
from micronaut.retry.annotation import Fallback
# end::imports[]

Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Fallback
class PetFallback(PetOperations):

    @SingleResult
    def save(self, name: str, age: int) -> Publisher:
        pet = Pet(name=name, age=age)
        return Mono.just(pet)
# end::class[]
