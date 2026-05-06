# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Controller, Get, Produces
# end::imports[]


@Requires(property="spec.name", value="producesspec")
# tag::clazz[]
@Controller("/produces")
class ProducesController:

    @Get  # <1>
    def index(self) -> HttpResponse:
        return HttpResponse.ok().body("{\"msg\":\"This is JSON\"}")

    @Produces(MediaType.TEXT_HTML)
    @Get("/html")  # <2>
    def html(self) -> str:
        return "<html><title><h1>HTML</h1></title><body></body></html>"

    @Get(value="/xml", produces=MediaType.TEXT_XML)  # <3>
    def xml(self) -> str:
        return "<html><title><h1>XML</h1></title><body></body></html>"
# end::clazz[]
