from typing import Annotated

from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.server.limit import RequestLimiter
from micronaut.inject.qualifiers import Qualifiers
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="RequestLimitTest")
@Property(name="micronaut.server.limits.reports.max-concurrent", value="2")
@MicronautTest
class RequestLimitTest:
    client: Annotated[HttpClient, Inject, Client("/")]
    context: Annotated[ApplicationContext, Inject]

    @Test
    def both_routes_go_through_the_named_limiter(self):
        limiter = self.context.getBean(RequestLimiter, Qualifiers.byName("reports"))
        assert self.client.toBlocking().retrieve(HttpRequest.GET("/limited-reports/1")) == "report 1"
        assert self.client.toBlocking().retrieve(HttpRequest.GET("/limited-reports/functional/2")) == "report 2"
        assert limiter.getAcquiredCount() == 2
