# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get, Produces
from micronaut.http.server.cors import CrossOrigin
# end::imports[]


@Requires(property="spec.name", value="CorsControllerSpec")
# tag::controller[]
@Controller("/hello")
class CorsController:
    @CrossOrigin("https://myui.com")  # <1>
    @Get(produces=MediaType.TEXT_PLAIN)  # <2>
    def cors(self) -> str:
        return "Welcome to the worlds of CORS"

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/nocors")  # <3>
    def nocorstoday(self) -> str:
        return "No more CORS for you"
# end::controller[]
