import java

from .Pet import Pet
from .PetOperations import PetOperations

# tag::imports[]
from micronaut.http.annotation import Controller, Post
# end::imports[]

Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Controller("/pets")
class PetController(PetOperations):

    # TODO: Re-enable @SingleResult when Python can import io.micronaut.core.async.annotation.SingleResult.
    # @SingleResult
    @Post
    def save(self, name: str, age: int) -> Publisher:
        pet = Pet(name=name, age=age)
        # save to database or something
        return Mono.just(pet)
# end::class[]
