from typing import Annotated

from micronaut.context.annotation import Requires
# tag::imports[]
import java
from jakarta.inject import Singleton
from micronaut.context.annotation import Value
from micronaut.http.gateway import GatewayFilters, GatewayRetry, GatewayRouteBuilder, GatewayRoutes
from micronaut.web.router.builder import RequestCondition

Duration = java.type("java.time.Duration")
# end::imports[]


@Requires(property="spec.name", value="GatewayRoutesTest")
# tag::clazz[]
@Singleton
class ApiGateway(GatewayRoutes):
    orders: Annotated[str, Value("${orders.url}")]  # <1>

    def routes(self, gateway: GatewayRouteBuilder) -> None:
        (gateway.route("orders")
            .path("/api/orders/**")  # <2>
            .where(RequestCondition.header("X-Tenant"))  # <3>
            .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addRequestHeader("X-Gateway", "mn"))  # <4>
            .retry(GatewayRetry.of(2))  # <5>
            .responseTimeout(Duration.ofSeconds(5))
            .uri(self.orders))
# end::clazz[]
