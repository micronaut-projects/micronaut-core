# tag::imports[]
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get
# end::imports[]
from micronaut.context.annotation import Requires

@Requires(missingProperty="spec.name")
# tag::class[]
@Controller("/hello") # <1>
class HelloController:
    # TODO: Fix ref GR-71394
    @Get(produces = "text/plain") # <2>
    def index(self) -> str:
        return "Hello World" # <3>
# end::class[]
