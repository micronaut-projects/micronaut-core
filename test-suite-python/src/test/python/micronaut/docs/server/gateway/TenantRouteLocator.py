from typing import Annotated

import java
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires, Value
from micronaut.http.gateway import GatewayFilters, GatewayRoute, GatewayRouteLocator

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
List = java.type("java.util.List")


@Requires(property="spec.name", value="GatewayDocsTest")
# tag::clazz[]
@Singleton
class TenantRouteLocator(GatewayRouteLocator):
    upstream: Annotated[str, Value("${orders.url}")]

    def getRoutes(self):
        routes = [GatewayRoute.builder("tenant-" + tenant)  # <1>
                  .path("/tenants/" + tenant + "/**")
                  .filters(GatewayFilters.stripPrefix(2), GatewayFilters.addRequestHeader("X-Tenant", tenant))
                  .uri(self.upstream)  # <2>
                  .build()
                  for tenant in ["acme", "globex"]]
        return CompletableFuture.completedFuture(List.copyOf(routes))
# end::clazz[]
