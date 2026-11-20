from typing import Annotated

from jakarta.inject import Singleton
from micronaut.context.annotation import Requires, Value
from micronaut.http.gateway import GatewayRouteBuilder, GatewayRoutes
from micronaut.web.router.builder import RequestCondition


@Requires(property="spec.name", value="GatewayDocsTest")
# tag::clazz[]
@Singleton
class BodyRoutes(GatewayRoutes):
    upstream: Annotated[str, Value("${orders.url}")]

    def routes(self, gateway: GatewayRouteBuilder) -> None:
        (gateway.route("express-orders").path("/orders/**").order(1)
            .where(RequestCondition.bodyText(lambda body: "express" in body))
            .uri(self.upstream + "/express"))
        (gateway.route("orders").path("/orders/**").order(2)
            .uri(self.upstream + "/standard"))
# end::clazz[]
