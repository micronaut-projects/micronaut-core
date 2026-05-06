from micronaut.context.annotation import Requires

# tag::imports[]
from micronaut.http.annotation import Controller, Get, RouteCondition
# end::imports[]


@Requires(property="spec.name", value="RouteConditionControllerSpec")
# tag::class[]
@Controller("/api")
class RouteConditionController:
    @Get("/hello")
    @RouteCondition("#{request.parameters.getFirst('v').orElse(null) != '2'}")
    def hello_v1(self) -> str:
        return "Hello v1"

    @Get("/hello")
    @RouteCondition("#{request.parameters.getFirst('v').orElse(null) == '2'}")  # <1>
    def hello_v2(self) -> str:
        return "Hello v2"
# end::class[]
