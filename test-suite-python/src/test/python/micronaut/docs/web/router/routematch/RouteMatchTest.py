from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property, Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

HttpRequest = java.type("io.micronaut.http.HttpRequest")
RouteAttributes = java.type("io.micronaut.web.router.RouteAttributes")


@Property(name="spec.name", value="RouteMatchSpec")
@MicronautTest
class RouteMatchTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_route_match_retrieval(self):
        blocking_client = self.client.toBlocking()
        request = HttpRequest.GET("/routeMatch").accept(MediaType.TEXT_PLAIN)
        assert blocking_client.retrieve(request, java.type("java.lang.String")) == "text/plain"


@Requires(property="spec.name", value="RouteMatchSpec")
@Controller
class RouteMatchController:
    @Get("/routeMatch", produces=MediaType.TEXT_PLAIN)
    # tag::routematch[]
    def index(self, request: HttpRequest) -> str:
        route_match = RouteAttributes.getRouteMatch(request).orElse(None)
    # end::routematch[]
        if route_match is None:
            return None
        produces = route_match.getRouteInfo().getProduces()
        return produces.stream().map(lambda media_type: str(media_type)).findFirst().orElse(None)
