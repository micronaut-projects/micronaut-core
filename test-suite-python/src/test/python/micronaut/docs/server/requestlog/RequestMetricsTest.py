import time
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .RequestMetrics import RequestMetrics


@Property(name="spec.name", value="RequestMetricsTest")
@Property(name="micronaut.server.netty.request-log-enabled", value="true")
@MicronautTest
class RequestMetricsTest:
    client: Annotated[HttpClient, Inject, Client("/")]
    metrics: Annotated[RequestMetrics, Inject]

    @Test
    def the_listener_sees_the_route_template(self):
        assert self.client.toBlocking().retrieve(HttpRequest.GET("/books/1")) == "book 1"
        deadline = time.time() + 10
        while self.metrics.nanos_by_route.isEmpty() and time.time() < deadline:
            time.sleep(0.01)
        assert self.metrics.nanos_by_route.containsKey("/books/{id} 200"), str(self.metrics.nanos_by_route)
