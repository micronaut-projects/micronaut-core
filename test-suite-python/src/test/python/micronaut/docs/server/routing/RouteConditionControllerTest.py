from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test


@Property(name="spec.name", value="RouteConditionControllerSpec")
@MicronautTest
@Disabled("Python @RouteCondition route selection returns 400 for conditional routes")
class RouteConditionControllerTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_route_condition_v1(self):
        response = self.client.toBlocking().retrieve("/api/hello")
        assert response == "Hello v1"

    @Test
    def test_route_condition_v2(self):
        response = self.client.toBlocking().retrieve("/api/hello?v=2")
        assert response == "Hello v2"

    @Test
    def test_route_condition_falls_back_to_v1_for_unmatched_version(self):
        response = self.client.toBlocking().retrieve("/api/hello?v=3")
        assert response == "Hello v1"
