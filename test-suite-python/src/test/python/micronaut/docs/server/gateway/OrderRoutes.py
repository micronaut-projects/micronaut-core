from typing import Annotated

from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Value
from micronaut.http.gateway import GatewayFilters, GatewayRoute, ProxyHandlerFactory
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes
# end::imports[]


@Requires(property="spec.name", value="GatewayRoutesTest")
# tag::clazz[]
@Singleton
class OrderRoutes(HttpRoutes):
    proxies: Annotated[ProxyHandlerFactory, Inject]
    orders: Annotated[str, Value("${orders.url}")]

    def routes(self, routes: HttpRouteBuilder) -> None:
        handler = self.proxies.create(GatewayRoute.builder("internal-orders")  # <1>
                                      .uri(self.orders)
                                      .filters(GatewayFilters.stripPrefix(1))
                                      .build())

        def internal(group):  # <2>
            group.any("").consumesAll().keepErrorResponses().handleAsync(handler)
            group.any("/{+path}").consumesAll().keepErrorResponses().handleAsync(handler)

        routes.path("/internal", internal)
# end::clazz[]
