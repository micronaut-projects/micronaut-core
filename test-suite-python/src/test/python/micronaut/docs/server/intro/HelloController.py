# tag::imports[]
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get
# end::imports[]
from micronaut.context.annotation import Requires

@Requires(missingProperty="spec.name")
# tag::class[]
@Controller("/hello") # <1>
class HelloController:
    @Get(produces=MediaType.TEXT_PLAIN) # <2>
    def index(self) -> str:
        return "Hello World" # <3>
# end::class[]
