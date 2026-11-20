from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Factory, Requires
from micronaut.http import MutableHttpRequest, MutableHttpResponse
from micronaut.http.gateway import GatewayFilter, ProxyExchange
from micronaut.http.gateway.config import GatewayFilterFactory


# tag::clazz[]
class AuditFilter(GatewayFilter):

    def __init__(self, team: str):
        self.team = team

    def filterRequest(self, exchange: ProxyExchange, request: MutableHttpRequest) -> None:  # <1>
        request.getHeaders().set("X-Audit-Team", self.team)

    def filterResponse(self, exchange: ProxyExchange, response: MutableHttpResponse) -> None:  # <2>
        response.getHeaders().set("X-Audited-Route", exchange.routeId())


@Factory
@Requires(property="spec.name", value="GatewayDocsTest")
class AuditFilterFactory:

    @Singleton
    @Named("Audit")
    def audit(self) -> GatewayFilterFactory:
        return GatewayFilterFactory.of(["team"], lambda args: AuditFilter(args.get("team")))  # <3>
# end::clazz[]
