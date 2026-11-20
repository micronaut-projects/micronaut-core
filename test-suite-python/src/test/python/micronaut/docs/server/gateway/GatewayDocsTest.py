from typing import Annotated

import java
from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Property, Requires
from micronaut.http import HttpRequest, HttpResponse, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.http.gateway import GatewayObserver
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes
from org.junit.jupiter.api import Test

from .RouteMetrics import RouteMetrics

String = java.type("java.lang.String")


def _text(value):
    return HttpResponse.ok(value).contentType(MediaType.TEXT_PLAIN_TYPE)


@Requires(property="spec.name", value="GatewayDocsTest")
@Singleton
class DocsUpstream(HttpRoutes):
    def routes(self, routes: HttpRouteBuilder) -> None:
        routes.POST("/express/{+path}", lambda request, path_variables: _text("express"))
        routes.POST("/standard/{+path}", lambda request, path_variables: _text("standard"))
        routes.GET("/echo", lambda request, path_variables: _text(
            "tenant=" + (request.getHeaders().get("X-Tenant") or "-")
            + " team=" + (request.getHeaders().get("X-Audit-Team") or "-")))


@Property(name="spec.name", value="GatewayDocsTest")
@Property(name="micronaut.server.port", value="${gateway.docs.port}")
@Property(name="gateway.docs.port", value="${random.port}")
# the application is its own upstream: the routes of DocsUpstream above
@Property(name="orders.url", value="http://localhost:${gateway.docs.port}")
@Property(name="micronaut.gateway.routes.audited.uri", value="${orders.url}")
@Property(name="micronaut.gateway.routes.audited.predicates[0]", value="Path=/audited/**")
@Property(name="micronaut.gateway.routes.audited.filters[0]", value="StripPrefix=1")
@Property(name="micronaut.gateway.routes.audited.filters[1]", value="Audit=billing")
@Property(name="micronaut.gateway.routes.limited.uri", value="${orders.url}")
@Property(name="micronaut.gateway.routes.limited.predicates[0]", value="Path=/limited/**")
@Property(name="micronaut.gateway.routes.limited.filters[0]", value="StripPrefix=1")
@Property(name="micronaut.gateway.routes.limited.filters[1]", value="RequestRateLimiter=1, 1, 1, tenant")
@Property(name="micronaut.gateway.routes.guarded.uri", value="http://127.0.0.1:1")
@Property(name="micronaut.gateway.routes.guarded.predicates[0]", value="Path=/guarded/**")
@Property(name="micronaut.gateway.routes.guarded.filters[0]", value="CircuitBreaker=orders, ordersFallback")
@MicronautTest
class GatewayDocsTest:
    client: Annotated[HttpClient, Inject, Client("/")]
    metrics: Annotated[RouteMetrics, Inject]

    @Test
    def the_samples_of_the_gateway_guide(self):
        http = self.client.toBlocking()
        assert http.retrieve(HttpRequest.POST("/orders/1", '{"express":true}').contentType(MediaType.APPLICATION_JSON_TYPE)) == "express"
        assert http.retrieve(HttpRequest.POST("/orders/1", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)) == "standard"
        assert http.retrieve(HttpRequest.GET("/tenants/acme/echo")) == "tenant=acme team=-"
        audited = http.exchange(HttpRequest.GET("/audited/echo"), String)
        assert audited.body() == "tenant=- team=billing"
        assert audited.getHeaders().get("X-Audited-Route") == "audited"
        assert http.exchange(HttpRequest.GET("/limited/echo").header("X-Tenant", "a"), String).code() == 200
        try:
            http.exchange(HttpRequest.GET("/limited/echo").header("X-Tenant", "a"), String)
            raise AssertionError("the rate limit let a second request through")
        except HttpClientResponseException as e:
            assert e.code() == 429
        assert http.retrieve(HttpRequest.GET("/guarded/x")).startswith("fallback: ")
        assert self.metrics.count("audited", GatewayObserver.Outcome.RESPONSE, 200) == 1
