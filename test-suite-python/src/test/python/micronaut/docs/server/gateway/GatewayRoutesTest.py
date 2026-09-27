from typing import Annotated

from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Property, Requires
from micronaut.http import HttpRequest, HttpResponse, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes
from org.junit.jupiter.api import Test


@Requires(property="spec.name", value="GatewayRoutesTest")
@Singleton
class Upstream(HttpRoutes):
    def routes(self, routes: HttpRouteBuilder) -> None:
        routes.GET("/orders/{id}", lambda request, path_variables: HttpResponse.ok(
            "order " + path_variables.getString("id") + " " + (request.getHeaders().get("X-Gateway") or "-"))
            .contentType(MediaType.TEXT_PLAIN_TYPE))


@Property(name="spec.name", value="GatewayRoutesTest")
@Property(name="micronaut.server.port", value="${gateway.test.port}")
@Property(name="gateway.test.port", value="${random.port}")
# the application is its own upstream: /orders/{id} above
@Property(name="orders.url", value="http://localhost:${gateway.test.port}")
@MicronautTest
class GatewayRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def the_gateway_routes_relay_to_the_upstream(self):
        http = self.client.toBlocking()
        assert http.retrieve(HttpRequest.GET("/api/orders/7").header("X-Tenant", "t")) == "order 7 mn"
        assert http.retrieve(HttpRequest.GET("/internal/orders/8")) == "order 8 -"
        try:
            http.retrieve(HttpRequest.GET("/api/orders/7"))
            raise AssertionError("the condition of the route matched")
        except HttpClientResponseException:
            pass
