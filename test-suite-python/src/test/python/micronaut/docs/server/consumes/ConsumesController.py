# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Consumes, Controller, Post
# end::imports[]


@Requires(property="spec.name", value="consumesspec")
# tag::clazz[]
@Controller("/consumes")
class ConsumesController:

    @Post  # <1>
    def index(self) -> HttpResponse:
        return HttpResponse.ok()

    @Consumes([MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON])  # <2>
    @Post("/multiple")
    def multipleConsumes(self) -> HttpResponse:
        return HttpResponse.ok()

    @Post(value="/member", consumes=MediaType.TEXT_PLAIN)  # <3>
    def consumesMember(self) -> HttpResponse:
        return HttpResponse.ok()
# end::clazz[]
