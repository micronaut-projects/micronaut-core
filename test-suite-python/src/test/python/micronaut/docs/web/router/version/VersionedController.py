from micronaut.context.annotation import Requires
# tag::imports[]
from micronaut.core.version.annotation import Version
from micronaut.http.annotation import Controller, Get
# end::imports[]


@Requires(property="spec.name", value="VersionedControllerSpec")
# tag::clazz[]
@Controller("/versioned")
class VersionedController:

    @Version("1")  # <1>
    @Get("/hello")
    def helloV1(self) -> str:
        return "helloV1"

    @Version("2")  # <2>
    @Get("/hello")
    def helloV2(self) -> str:
        return "helloV2"
# end::clazz[]
