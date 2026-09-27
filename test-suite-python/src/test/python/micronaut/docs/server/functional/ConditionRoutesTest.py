from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.runtime.server import EmbeddedServer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .ConditionRoutes import ConditionRoutes

URI = java.type("java.net.URI")


@Property(name="spec.name", value="ConditionRoutesTest")
@Property(name="management.port", value="${random.port}")
@MicronautTest
class ConditionRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]
    server: Annotated[EmbeddedServer, Inject]
    condition_routes: Annotated[ConditionRoutes, Inject]

    def _status(self, request) -> HttpStatus:
        try:
            self.client.toBlocking().retrieve(request)
        except HttpClientResponseException as e:
            return e.getStatus()
        raise AssertionError("the request did not fail")

    @Test
    def a_condition_selects_the_route(self):
        http = self.client.toBlocking()
        assert http.retrieve(HttpRequest.GET("/search")) == "search"
        assert http.retrieve(HttpRequest.GET("/search").header("X-Beta", "on")) == "beta search"
        assert http.retrieve(HttpRequest.GET("/search?beta=true")) == "beta search"

    @Test
    def a_constraint_on_the_path_variables(self):
        http = self.client.toBlocking()
        assert http.retrieve(HttpRequest.GET("/shops/north/stock")) == "stock of north"
        assert self._status(HttpRequest.GET("/shops/west/stock")) == HttpStatus.NOT_FOUND
        assert http.retrieve(HttpRequest.GET("/items/5")) == "item 5"
        assert http.retrieve(HttpRequest.GET("/items/lamp")) == "item named lamp"
        assert http.retrieve(HttpRequest.GET("/items/-1")) == "item named -1"

    @Test
    def a_filter_reads_the_attributes_of_the_route(self):
        http = self.client.toBlocking()
        assert http.retrieve(HttpRequest.GET("/reports/daily").header("X-Role", "auditor")) == "daily report"
        assert self._status(HttpRequest.GET("/reports/salaries").header("X-Role", "auditor")) == HttpStatus.FORBIDDEN
        assert http.retrieve(HttpRequest.GET("/reports/salaries").header("X-Role", "admin")) == "salaries"

    @Test
    def a_route_on_another_port(self):
        assert self._status(HttpRequest.GET("/management/health")) == HttpStatus.NOT_FOUND
        port = self.condition_routes.management_port
        management = HttpClient.create(URI.create(f"http://{self.server.getHost()}:{port}").toURL())
        try:
            assert management.toBlocking().retrieve(HttpRequest.GET("/management/health")) == "UP"
        finally:
            management.close()
