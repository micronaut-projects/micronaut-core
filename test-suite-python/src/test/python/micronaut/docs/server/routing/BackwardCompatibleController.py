from micronaut.context.annotation import Requires

# tag::imports[]
from micronaut.http.annotation import Controller, Get
# end::imports[]


@Requires(property="spec.name", value="BackwardCompatibleControllerSpec")
# tag::class[]
@Controller("/hello")
class BackwardCompatibleController:
    @Get(uris=["/{name}", "/person/{name}"])  # <1>
    def hello(self, name: str) -> str:  # <2>
        return f"Hello, {name}"
# end::class[]
