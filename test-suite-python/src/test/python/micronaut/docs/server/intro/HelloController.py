# tag::imports[]
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get
# end::imports[]

# tag::class[]
@Controller("/hello") # <1>
class HelloController:
    # TODO: Fix  ref
    @Get(produces = "text/plain") # <2>
    def index(self) -> str:
        return "Hello World" # <3>
# end::class[]
